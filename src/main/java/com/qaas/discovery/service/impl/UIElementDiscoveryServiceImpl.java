package com.qaas.discovery.service.impl;

import com.qaas.discovery.entity.UIElement;
import com.qaas.discovery.repository.UIElementRepository;
import com.qaas.discovery.service.UIElementDiscoveryService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class UIElementDiscoveryServiceImpl implements UIElementDiscoveryService {

    private final UIElementRepository repository;

    public UIElementDiscoveryServiceImpl(UIElementRepository repository) { this.repository = repository; }

    @Override
    public List<UIElement> discover(UUID pageId, String htmlContent) {
        List<UIElement> out = new ArrayList<>();
        if (htmlContent == null || htmlContent.isBlank()) return out;
        try {
            Document doc = Jsoup.parse(htmlContent);
            Elements elements = doc.select("input, button, select, textarea, a[href], form");
            for (Element e : elements) {
                UIElement u = new UIElement();
                u.setPageId(pageId);
                u.setSelector(e.cssSelector());
                u.setElementType(e.tagName());
                String label = e.attr("aria-label");
                if (label.isBlank()) label = e.attr("name");
                if (label.isBlank()) label = e.attr("placeholder");
                if (label.isBlank()) label = e.attr("type");
                if (label.isBlank()) {
                    String text = e.text();
                    label = text.length() > 80 ? text.substring(0, 80) : text;
                }
                u.setLabel(label);
                repository.save(u);
                out.add(u);
            }
        } catch (Exception ignored) {}
        return out;
    }
}