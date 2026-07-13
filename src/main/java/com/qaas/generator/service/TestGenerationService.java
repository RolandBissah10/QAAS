package com.qaas.generator.service;

import com.qaas.generator.entity.GeneratedTest;

import java.util.List;
import java.util.UUID;

public interface TestGenerationService {
    List<GeneratedTest> generateForPage(UUID pageId, String pageUrl, String htmlContent);
}
