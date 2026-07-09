package com.qaas.playwright.service;

import com.qaas.evidence.entity.Screenshot;

import java.util.UUID;

public interface PlaywrightService {
    Screenshot captureScreenshot(UUID analysisId, String url) throws Exception;
}
