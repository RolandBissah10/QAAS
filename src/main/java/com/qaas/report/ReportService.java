package com.qaas.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.qaas.bug.BugRepository;
import com.qaas.execution.ExecutionStatus;
import com.qaas.execution.TestExecutionRepository;
import com.qaas.page.repository.PageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final String REPORT_DIR =
            System.getenv().getOrDefault("REPORT_DIR", "reports");

    private static final Color BRAND   = new Color(20, 184, 166);
    private static final Color DARK    = new Color(30, 41, 59);
    private static final Color MUTED   = new Color(100, 116, 139);
    private static final Color HEAD_BG = new Color(248, 250, 252);
    private static final Color PASS_BG = new Color(209, 250, 229);
    private static final Color FAIL_BG = new Color(254, 226, 226);
    private static final Color HIGH_BG = new Color(255, 237, 213);

    private final ReportRepository reports;
    private final PageRepository pages;
    private final TestExecutionRepository executions;
    private final BugRepository bugs;
    private final ObjectMapper json;

    public ReportService(ReportRepository reports, PageRepository pages,
                         TestExecutionRepository executions, BugRepository bugs) {
        this.reports = reports;
        this.pages = pages;
        this.executions = executions;
        this.bugs = bugs;
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Transactional
    public Report generate(UUID analysisId, ReportFormat format) {
        Report existing = reports.findByAnalysisIdAndFormat(analysisId, format).orElse(null);
        if (existing != null) return existing;

        int pageCount = pages.findByAnalysisId(analysisId).size();
        var execs = executions.findByAnalysisId(analysisId);
        int total = execs.size();
        int passed = (int) execs.stream().filter(e -> e.getStatus() == ExecutionStatus.PASSED).count();
        int failed = total - passed;
        int bugCount = bugs.findByAnalysisId(analysisId).size();
        int score = total == 0 ? 0 : (int) Math.round((passed * 100.0) / total);

        new File(REPORT_DIR).mkdirs();
        String filePath = REPORT_DIR + "/" + analysisId + "." + format.name().toLowerCase();

        Report report = new Report(analysisId, format);
        report.setPagesDiscovered(pageCount);
        report.setTotalTests(total);
        report.setPassedTests(passed);
        report.setFailedTests(failed);
        report.setBugCount(bugCount);
        report.setQualityScore(score);
        report.setFilePath(filePath);
        report = reports.save(report);

        try {
            if (format == ReportFormat.JSON) writeJson(report, analysisId);
            else if (format == ReportFormat.HTML) writeHtml(report, analysisId);
            else if (format == ReportFormat.PDF)  writePdf(report, analysisId);
        } catch (Exception e) {
            log.warn("Could not write {} report file: {}", format, e.getMessage());
        }

        return report;
    }

    // -------------------------------------------------------------------------
    // PDF
    // -------------------------------------------------------------------------

    private void writePdf(Report report, UUID analysisId) throws Exception {
        var pageList = pages.findByAnalysisId(analysisId);
        var bugList  = bugs.findByAnalysisId(analysisId);

        Font titleFont   = new Font(Font.HELVETICA, 22, Font.BOLD,   BRAND);
        Font scoreFont   = new Font(Font.HELVETICA, 44, Font.BOLD,   BRAND);
        Font scoreLbl    = new Font(Font.HELVETICA, 10, Font.NORMAL, MUTED);
        Font sectionFont = new Font(Font.HELVETICA, 13, Font.BOLD,   DARK);
        Font metaFont    = new Font(Font.HELVETICA,  9, Font.NORMAL, MUTED);
        Font bodyFont    = new Font(Font.HELVETICA,  9, Font.NORMAL, DARK);
        Font hdrFont     = new Font(Font.HELVETICA,  9, Font.BOLD,   new Color(71, 85, 105));

        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        PdfWriter.getInstance(doc, new FileOutputStream(report.getFilePath()));
        doc.open();

        doc.add(new Paragraph("QAAS Quality Report", titleFont));
        Paragraph meta = new Paragraph("Analysis: " + analysisId
                + "   |   Generated: " + report.getGeneratedAt(), metaFont);
        doc.add(meta);
        doc.add(Chunk.NEWLINE);

        doc.add(new Paragraph((report.getQualityScore() != null ? report.getQualityScore() : 0) + "%", scoreFont));
        doc.add(new Paragraph("Quality Score", scoreLbl));
        doc.add(Chunk.NEWLINE);

        // Summary table
        doc.add(new Paragraph("Summary", sectionFont));
        doc.add(Chunk.NEWLINE);
        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(55);
        summary.setHorizontalAlignment(Element.ALIGN_LEFT);
        pdfRow2(summary, "Metric",           "Value",                        hdrFont,  HEAD_BG);
        pdfRow2(summary, "Pages Discovered", str(report.getPagesDiscovered()), bodyFont, Color.WHITE);
        pdfRow2(summary, "Tests Executed",   str(report.getTotalTests()),      bodyFont, HEAD_BG);
        pdfRow2(summary, "Tests Passed",     str(report.getPassedTests()),     bodyFont, PASS_BG);
        pdfRow2(summary, "Tests Failed",     str(report.getFailedTests()),     bodyFont,
                report.getFailedTests() != null && report.getFailedTests() > 0 ? FAIL_BG : Color.WHITE);
        pdfRow2(summary, "Bugs Detected",    str(report.getBugCount()),        bodyFont,
                report.getBugCount() != null && report.getBugCount() > 0 ? FAIL_BG : Color.WHITE);
        doc.add(summary);
        doc.add(Chunk.NEWLINE);

        // Pages table
        doc.add(new Paragraph("Discovered Pages (" + pageList.size() + ")", sectionFont));
        doc.add(Chunk.NEWLINE);
        if (pageList.isEmpty()) {
            doc.add(new Paragraph("No pages recorded.", bodyFont));
        } else {
            PdfPTable pagesTable = new PdfPTable(3);
            pagesTable.setWidthPercentage(100);
            pagesTable.setWidths(new float[]{4f, 2f, 1.5f});
            pdfRow3(pagesTable, "URL", "Title", "Type", hdrFont, HEAD_BG);
            for (var p : pageList) {
                pdfRow3(pagesTable,
                        p.getUrl(),
                        p.getTitle() != null ? p.getTitle() : "—",
                        p.getPageType() != null ? p.getPageType() : "GENERAL",
                        bodyFont, Color.WHITE);
            }
            doc.add(pagesTable);
        }
        doc.add(Chunk.NEWLINE);

        // Bugs table
        doc.add(new Paragraph("Bugs Detected (" + bugList.size() + ")", sectionFont));
        doc.add(Chunk.NEWLINE);
        if (bugList.isEmpty()) {
            doc.add(new Paragraph("No bugs detected. All tests passed.", bodyFont));
        } else {
            PdfPTable bugsTable = new PdfPTable(3);
            bugsTable.setWidthPercentage(100);
            bugsTable.setWidths(new float[]{3f, 1.2f, 4f});
            pdfRow3(bugsTable, "Title", "Severity", "Description", hdrFont, HEAD_BG);
            for (var b : bugList) {
                Color sevBg = switch (b.getSeverity()) {
                    case CRITICAL -> FAIL_BG;
                    case HIGH     -> HIGH_BG;
                    default       -> Color.WHITE;
                };
                pdfRow3(bugsTable,
                        b.getTitle(),
                        b.getSeverity().name(),
                        b.getDescription() != null ? b.getDescription() : "—",
                        bodyFont, sevBg);
            }
            doc.add(bugsTable);
        }

        doc.close();
        log.info("PDF report written to {}", report.getFilePath());
    }

    private static String str(Integer v) {
        return v != null ? String.valueOf(v) : "0";
    }

    private static void pdfRow2(PdfPTable table, String a, String b, Font font, Color bg) {
        for (String text : new String[]{a, b}) {
            PdfPCell cell = new PdfPCell(new Phrase(text, font));
            cell.setBackgroundColor(bg);
            cell.setPadding(6);
            cell.setBorderColor(new Color(226, 232, 240));
            table.addCell(cell);
        }
    }

    private static void pdfRow3(PdfPTable table, String a, String b, String c, Font font, Color bg) {
        for (String text : new String[]{a, b, c}) {
            PdfPCell cell = new PdfPCell(new Phrase(text, font));
            cell.setBackgroundColor(bg);
            cell.setPadding(6);
            cell.setBorderColor(new Color(226, 232, 240));
            table.addCell(cell);
        }
    }

    // -------------------------------------------------------------------------
    // JSON
    // -------------------------------------------------------------------------

    private void writeJson(Report report, UUID analysisId) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("reportId", report.getId());
        data.put("analysisId", analysisId);
        data.put("generatedAt", report.getGeneratedAt());
        data.put("qualityScore", report.getQualityScore());
        data.put("summary", Map.of(
                "pagesDiscovered", report.getPagesDiscovered(),
                "totalTests", report.getTotalTests(),
                "passedTests", report.getPassedTests(),
                "failedTests", report.getFailedTests(),
                "bugsDetected", report.getBugCount()
        ));
        data.put("pages", pages.findByAnalysisId(analysisId).stream()
                .map(p -> Map.of(
                        "url", p.getUrl(),
                        "title", p.getTitle() != null ? p.getTitle() : "",
                        "pageType", p.getPageType() != null ? p.getPageType() : "GENERAL"))
                .toList());
        data.put("bugs", bugs.findByAnalysisId(analysisId).stream()
                .map(b -> Map.of(
                        "title", b.getTitle(),
                        "description", b.getDescription() != null ? b.getDescription() : "",
                        "severity", b.getSeverity().name()))
                .toList());
        json.writerWithDefaultPrettyPrinter().writeValue(new File(report.getFilePath()), data);
        log.info("JSON report written to {}", report.getFilePath());
    }

    // -------------------------------------------------------------------------
    // HTML
    // -------------------------------------------------------------------------

    private void writeHtml(Report report, UUID analysisId) throws Exception {
        var pageList = pages.findByAnalysisId(analysisId);
        var bugList = bugs.findByAnalysisId(analysisId);

        var html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
            .append("<title>QAAS Report</title>")
            .append("<style>")
            .append("body{font-family:Inter,sans-serif;max-width:900px;margin:40px auto;padding:0 20px;color:#1e293b;line-height:1.6}")
            .append("h1{color:#14b8a6;margin-bottom:4px}h2{color:#334155;border-bottom:2px solid #e2e8f0;padding-bottom:6px;margin-top:32px}")
            .append(".score{font-size:56px;font-weight:700;color:#14b8a6}.score-label{color:#64748b;font-size:14px}")
            .append(".badge{display:inline-block;padding:2px 10px;border-radius:99px;font-size:12px;font-weight:600}")
            .append(".pass{background:#d1fae5;color:#065f46}.fail{background:#fee2e2;color:#991b1b}")
            .append(".critical{background:#fee2e2;color:#991b1b}.high{background:#ffedd5;color:#9a3412}")
            .append(".medium{background:#fef9c3;color:#854d0e}.low{background:#dbeafe;color:#1e40af}")
            .append("table{width:100%;border-collapse:collapse;margin-top:8px}")
            .append("th,td{text-align:left;padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:14px}")
            .append("th{background:#f8fafc;font-weight:600;color:#475569}")
            .append(".meta{color:#64748b;font-size:14px}")
            .append("</style></head><body>");

        html.append("<h1>QAAS Quality Report</h1>")
            .append("<p class='meta'>Analysis: <code>").append(analysisId)
            .append("</code> &nbsp;|&nbsp; Generated: ").append(report.getGeneratedAt()).append("</p>")
            .append("<div class='score'>").append(report.getQualityScore()).append("%</div>")
            .append("<div class='score-label'>Quality Score</div>");

        html.append("<h2>Summary</h2>")
            .append("<table><tr><th>Metric</th><th>Value</th></tr>")
            .append("<tr><td>Pages Discovered</td><td>").append(report.getPagesDiscovered()).append("</td></tr>")
            .append("<tr><td>Tests Executed</td><td>").append(report.getTotalTests()).append("</td></tr>")
            .append("<tr><td>Passed</td><td><span class='badge pass'>").append(report.getPassedTests()).append("</span></td></tr>")
            .append("<tr><td>Failed</td><td><span class='badge fail'>").append(report.getFailedTests()).append("</span></td></tr>")
            .append("<tr><td>Bugs Detected</td><td>").append(report.getBugCount()).append("</td></tr>")
            .append("</table>");

        html.append("<h2>Discovered Pages (").append(pageList.size()).append(")</h2>")
            .append("<table><tr><th>URL</th><th>Title</th><th>Type</th></tr>");
        for (var p : pageList) {
            html.append("<tr><td>").append(p.getUrl())
                .append("</td><td>").append(p.getTitle() != null ? p.getTitle() : "—")
                .append("</td><td>").append(p.getPageType() != null ? p.getPageType() : "GENERAL")
                .append("</td></tr>");
        }
        html.append("</table>");

        html.append("<h2>Bugs Detected (").append(bugList.size()).append(")</h2>");
        if (bugList.isEmpty()) {
            html.append("<p>No bugs detected.</p>");
        } else {
            html.append("<table><tr><th>Title</th><th>Severity</th><th>Description</th></tr>");
            for (var b : bugList) {
                String sevClass = b.getSeverity().name().toLowerCase();
                html.append("<tr><td>").append(b.getTitle())
                    .append("</td><td><span class='badge ").append(sevClass).append("'>")
                    .append(b.getSeverity().name()).append("</span></td><td>")
                    .append(b.getDescription() != null ? b.getDescription() : "—")
                    .append("</td></tr>");
            }
            html.append("</table>");
        }
        html.append("</body></html>");

        try (FileWriter fw = new FileWriter(report.getFilePath(), StandardCharsets.UTF_8)) {
            fw.write(html.toString());
        }
        log.info("HTML report written to {}", report.getFilePath());
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.ReportResponse> getByAnalysis(UUID analysisId) {
        return reports.findByAnalysisId(analysisId)
                .stream()
                .map(ReportDtos.ReportResponse::from)
                .toList();
    }
}