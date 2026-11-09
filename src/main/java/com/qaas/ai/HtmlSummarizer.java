package com.qaas.ai;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Strips a full page HTML down to the controls and content the AI test generator needs
 * to generate meaningful test steps — keeps token count low and signal high.
 */
@Component
public class HtmlSummarizer {

    private static final String INTERACTIVE_SELECTOR =
            "main, nav, form, input, button, select, textarea, a[href], h1, h2, h3, "
                    + "label, option, table, th, td, [role=button], [role=tab], [role=alert]";

    private static final Set<String> KEEP_ATTRS = Set.of(
            "id", "name", "type", "placeholder", "aria-label",
            "href", "action", "method", "value", "for", "role", "title", "alt", "data-testid");

    private static final int MAX_CHARS = 10000;

    public String summarize(String html) {
        if (html == null || html.isBlank()) return "";
        try {
            Document doc = Jsoup.parse(html);
            doc.select("script, style, svg, meta, link, noscript, head").remove();

            Elements elements = doc.select(INTERACTIVE_SELECTOR);
            StringBuilder sb = new StringBuilder();

            for (Element el : elements) {
                sb.append('<').append(el.tagName());
                for (Attribute attr : el.attributes()) {
                    if (KEEP_ATTRS.contains(attr.getKey()) && !attr.getValue().isBlank()) {
                        sb.append(' ').append(attr.getKey())
                          .append("=\"").append(attr.getValue()).append('"');
                    }
                }
                String text = el.ownText().trim().replaceAll("\\s+", " ");
                if (!text.isBlank()) {
                    String truncated = text.length() > 120 ? text.substring(0, 120) : text;
                    sb.append('>').append(truncated).append("</").append(el.tagName()).append('>');
                } else {
                    sb.append("/>");
                }
                sb.append('\n');
                if (sb.length() >= MAX_CHARS) break;
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}