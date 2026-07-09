package com.qaas.playwright.service.impl;

import com.microsoft.playwright.*;
import com.qaas.evidence.entity.Screenshot;
import com.qaas.evidence.repository.ScreenshotRepository;
import com.qaas.playwright.service.PlaywrightService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PlaywrightServiceImpl implements PlaywrightService {

    private static final String SCREENSHOT_DIR =
            System.getenv().getOrDefault("SCREENSHOT_DIR", "screenshots");

    private final ScreenshotRepository screenshotRepository;

    public PlaywrightServiceImpl(ScreenshotRepository screenshotRepository) {
        this.screenshotRepository = screenshotRepository;
    }

    @Override
    public Screenshot captureScreenshot(UUID analysisId, String url) throws Exception {
        try (Playwright pw = Playwright.create()) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setChromiumSandbox(false)
                    .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run"));
            Browser browser = pw.chromium().launch(opts);
            com.microsoft.playwright.Page page = browser.newPage();
            page.navigate(url, new com.microsoft.playwright.Page.NavigateOptions().setTimeout(15000));

            new File(SCREENSHOT_DIR).mkdirs();
            String filename = SCREENSHOT_DIR + "/" + UUID.randomUUID() + ".png";
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(java.nio.file.Path.of(filename))
                    .setFullPage(true));

            Screenshot s = new Screenshot();
            s.setAnalysisId(analysisId);
            s.setPath(filename);
            s.setCapturedAt(OffsetDateTime.now());
            screenshotRepository.save(s);
            browser.close();
            return s;
        }
    }
}