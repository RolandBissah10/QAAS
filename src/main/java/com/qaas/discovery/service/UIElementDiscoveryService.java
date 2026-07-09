package com.qaas.discovery.service;

import com.qaas.discovery.entity.UIElement;

import java.util.List;
import java.util.UUID;

public interface UIElementDiscoveryService {
    List<UIElement> discover(UUID pageId, String htmlContent);
}