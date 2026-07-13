package com.qaas.discovery.service.impl;

import com.qaas.discovery.entity.UIElement;
import com.qaas.discovery.repository.UIElementRepository;
import com.qaas.discovery.service.UIElementDiscoveryService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UIElementDiscoveryServiceImpl implements UIElementDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(UIElementDiscoveryServiceImpl.class);

    // Covers native HTML interactive elements + ARIA roles + test-id conventions
    private static final String SELECTOR =
            "input, button, select, textarea, a[href], form, label[for], img[alt], " +
            "[role=button], [role=link], [role=checkbox], [role=radio], " +
            "[role=tab], [role=menuitem], [role=textbox], [role=combobox], " +
            "[data-testid], [data-cy], [data-test]";

    private final UIElementRepository repository;

    public UIElementDiscoveryServiceImpl(UIElementRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<UIElement> discover(UUID pageId, String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) return List.of();
        try {
            Document doc = Jsoup.parse(htmlContent);
            Elements candidates = doc.select(SELECTOR);

            Set<String> seen = new HashSet<>();
            List<UIElement> out = new ArrayList<>();

            for (Element e : candidates) {
                String selector = e.cssSelector();
                // Skip duplicates that appear multiple times on the same page
                if (!seen.add(selector)) continue;

                UIElement u = new UIElement();
                u.setPageId(pageId);
                u.setSelector(selector.length() > 2000 ? selector.substring(0, 2000) : selector);
                u.setElementType(resolveType(e));
                u.setLabel(resolveLabel(e));
                out.add(u);
            }

            List<UIElement> saved = repository.saveAll(out);
            log.debug("Discovered {} UI elements for page {}", saved.size(), pageId);
            return saved;

        } catch (Exception ex) {
            log.warn("UI element discovery failed for page {}: {}", pageId, ex.getMessage());
            return List.of();
        }
    }

    private static String resolveType(Element e) {
        // Prefer ARIA role when present — it's semantically more precise than the tag
        String role = e.attr("role").trim();
        if (!role.isBlank()) return role;

        String tag = e.tagName();
        // Distinguish input subtypes so consumers know what they're dealing with
        if ("input".equals(tag)) {
            String type = e.attr("type").toLowerCase().trim();
            return type.isBlank() ? "input" : "input[" + type + "]";
        }
        return tag;
    }

    private static String resolveLabel(Element e) {
        String tag = e.tagName();

        // 1. Explicit aria-label — highest priority, set by developer for accessibility
        String label = e.attr("aria-label").trim();
        if (!label.isBlank()) return truncate(label);

        // 2. Visible text for buttons and links is the most meaningful label
        if ("a".equals(tag) || "button".equals(tag)) {
            String text = e.text().trim();
            if (!text.isBlank()) return truncate(text);
        }

        // 3. img → alt text
        if ("img".equals(tag)) {
            String alt = e.attr("alt").trim();
            if (!alt.isBlank()) return truncate(alt);
        }

        // 4. Form fields → name > placeholder > id
        String name = e.attr("name").trim();
        if (!name.isBlank()) return truncate(name);

        String placeholder = e.attr("placeholder").trim();
        if (!placeholder.isBlank()) return truncate(placeholder);

        String id = e.attr("id").trim();
        if (!id.isBlank()) return id;

        // 5. Test-id attributes — useful for automation context
        for (String attr : new String[]{"data-testid", "data-cy", "data-test"}) {
            String val = e.attr(attr).trim();
            if (!val.isBlank()) return val;
        }

        // 6. title attribute
        String title = e.attr("title").trim();
        if (!title.isBlank()) return truncate(title);

        // 7. Fallback: any visible text, then the type attribute
        String text = e.text().trim();
        if (!text.isBlank()) return truncate(text);

        String type = e.attr("type").trim();
        return type.isBlank() ? tag : type;
    }

    private static String truncate(String s) {
        return s.length() > 80 ? s.substring(0, 80) : s;
    }
}