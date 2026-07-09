package com.qaas.discovery.controller;

import com.qaas.discovery.entity.UIElement;
import com.qaas.discovery.repository.UIElementRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ui-elements")
public class UIElementController {

    private final UIElementRepository repository;

    public UIElementController(UIElementRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/page/{pageId}")
    List<UIElement> byPage(@PathVariable UUID pageId) {
        return repository.findByPageId(pageId);
    }
}