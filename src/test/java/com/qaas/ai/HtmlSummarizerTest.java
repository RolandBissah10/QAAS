package com.qaas.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSummarizerTest {

    private final HtmlSummarizer summarizer = new HtmlSummarizer();

    @Test
    void retainsApplicationContentAndAccessibleControls() {
        String summary = summarizer.summarize("""
                <html><body>
                  <main><h1>Job alerts</h1>
                    <nav><a href="/alerts">My alerts</a></nav>
                    <table><tr><th>Company</th><td>Example Corp</td></tr></table>
                    <button aria-label="Create job alert">Create</button>
                  </main>
                </body></html>
                """);

        assertThat(summary).contains("Job alerts", "href=\"/alerts\"", "Company",
                "Example Corp", "aria-label=\"Create job alert\"");
    }

    @Test
    void keepsMorePageContextThanThePreviousSummaryLimit() {
        StringBuilder html = new StringBuilder("<html><body><main>");
        for (int i = 0; i < 40; i++) {
            html.append("<button aria-label='action-").append(i).append("'>")
                    .append("Detailed application action ").append(i)
                    .append(" and its user-facing description</button>");
        }
        html.append("</main></body></html>");

        String summary = summarizer.summarize(html.toString());

        assertThat(summary).hasSizeGreaterThan(3500);
        assertThat(summary).contains("action-39");
    }
}
