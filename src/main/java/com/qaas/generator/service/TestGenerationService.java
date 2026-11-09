package com.qaas.generator.service;

import com.qaas.generator.entity.GeneratedTest;
import com.qaas.page.entity.Page;

import java.util.List;
import java.util.UUID;

public interface TestGenerationService {
    List<GeneratedTest> generateForPage(UUID pageId, String pageUrl, String htmlContent);
    List<GeneratedTest> generateJourneys(List<Page> pages, String baseUrl, boolean authenticated);
}
