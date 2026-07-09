package com.qaas.notification;

import com.qaas.config.AppProperties;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.report.Report;
import com.qaas.report.ReportFormat;
import com.qaas.report.ReportRepository;
import com.qaas.user.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final AppProperties properties;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final JavaMailSender mailSender;

    public EmailNotificationService(AppProperties properties,
                                    ProjectRepository projectRepository,
                                    UserRepository userRepository,
                                    ReportRepository reportRepository,
                                    JavaMailSender mailSender) {
        this.properties = properties;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
        this.mailSender = mailSender;
    }

    @Async
    @EventListener
    public void onAnalysisNotification(AnalysisNotificationEvent event) {
        if (!properties.mail().enabled()) {
            log.debug("Mail disabled — skipping notification for analysis {}", event.analysisId());
            return;
        }
        String toEmail = resolveOwnerEmail(event);
        if (toEmail == null) {
            log.warn("Could not resolve owner email for analysis {} — notification skipped", event.analysisId());
            return;
        }

        try {
            if ("COMPLETED".equals(event.status())) {
                sendCompleted(toEmail, event);
            } else if ("FAILED".equals(event.status())) {
                sendFailed(toEmail, event);
            }
        } catch (Exception e) {
            log.error("Failed to send email notification for analysis {}: {}", event.analysisId(), e.getMessage());
        }
    }

    private String resolveOwnerEmail(AnalysisNotificationEvent event) {
        return projectRepository.findById(event.projectId())
                .flatMap(project -> userRepository.findById(project.getOwnerId()))
                .map(user -> user.getEmail())
                .orElse(null);
    }

    private void sendCompleted(String to, AnalysisNotificationEvent event) throws Exception {
        Report report = reportRepository
                .findByAnalysisIdAndFormat(event.analysisId(), ReportFormat.JSON)
                .orElse(null);

        int score       = report != null && report.getQualityScore()    != null ? report.getQualityScore()    : 0;
        int pages       = report != null && report.getPagesDiscovered()  != null ? report.getPagesDiscovered()  : 0;
        int passed      = report != null && report.getPassedTests()      != null ? report.getPassedTests()      : 0;
        int failed      = report != null && report.getFailedTests()      != null ? report.getFailedTests()      : 0;
        int bugs        = report != null && report.getBugCount()         != null ? report.getBugCount()         : 0;

        String subject = "QAAS — Analysis complete for " + event.url();
        String body = buildHtml(
                "Analysis Complete",
                "#14b8a6",
                event.url(),
                "<p>The analysis finished successfully. Here is your quality summary:</p>"
                + "<table style='width:100%;border-collapse:collapse;margin:16px 0'>"
                + row("Quality Score", score + "%", score >= 80 ? "#d1fae5" : score >= 50 ? "#fef9c3" : "#fee2e2")
                + row("Pages Discovered", String.valueOf(pages), "#f8fafc")
                + row("Tests Passed",     String.valueOf(passed), "#d1fae5")
                + row("Tests Failed",     String.valueOf(failed), failed > 0 ? "#fee2e2" : "#f8fafc")
                + row("Bugs Detected",    String.valueOf(bugs),   bugs   > 0 ? "#fee2e2" : "#f8fafc")
                + "</table>"
                + "<p style='color:#64748b;font-size:14px'>Log in to the QAAS dashboard to view the full report and download PDF/HTML exports.</p>"
        );

        send(to, subject, body);
        log.info("Sent COMPLETED notification to {} for analysis {}", to, event.analysisId());
    }

    private void sendFailed(String to, AnalysisNotificationEvent event) throws Exception {
        String subject = "QAAS — Analysis failed for " + event.url();
        String body = buildHtml(
                "Analysis Failed",
                "#ef4444",
                event.url(),
                "<p>The analysis encountered an error and could not complete.</p>"
                + "<p>This may be due to a network issue, a login failure, or an unreachable URL.</p>"
                + "<p style='color:#64748b;font-size:14px'>Log in to the QAAS dashboard to review the details and retry the analysis.</p>"
        );

        send(to, subject, body);
        log.info("Sent FAILED notification to {} for analysis {}", to, event.analysisId());
    }

    private void send(String to, String subject, String html) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(properties.mail().from());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }

    private static String buildHtml(String heading, String headingColor, String url, String content) {
        return "<!DOCTYPE html><html><body style='font-family:Inter,Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1e293b'>"
                + "<h2 style='color:" + headingColor + ";margin-bottom:4px'>" + heading + "</h2>"
                + "<p style='color:#64748b;font-size:14px;margin-top:0'>URL: <strong>" + url + "</strong></p>"
                + "<hr style='border:none;border-top:1px solid #e2e8f0;margin:16px 0'>"
                + content
                + "<hr style='border:none;border-top:1px solid #e2e8f0;margin:24px 0'>"
                + "<p style='color:#94a3b8;font-size:12px'>You are receiving this because you are the project owner in QAAS.</p>"
                + "</body></html>";
    }

    private static String row(String label, String value, String bg) {
        return "<tr style='background:" + bg + "'>"
                + "<td style='padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:14px'>" + label + "</td>"
                + "<td style='padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:14px;font-weight:600'>" + value + "</td>"
                + "</tr>";
    }
}