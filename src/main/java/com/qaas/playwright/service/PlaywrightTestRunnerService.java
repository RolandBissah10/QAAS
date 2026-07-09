package com.qaas.playwright.service;

import com.qaas.generator.entity.GeneratedTest;

import java.util.List;
import java.util.UUID;

public interface PlaywrightTestRunnerService {
    List<GeneratedTest> runTestsForAnalysis(UUID analysisId) throws Exception;
    List<GeneratedTest> runAllTests() throws Exception;
}
