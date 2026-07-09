package com.qaas.playwright.service.impl;

import com.qaas.execution.ExecutionService;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.repository.GeneratedTestRepository;
import com.qaas.page.repository.PageRepository;
import com.qaas.playwright.service.PlaywrightTestRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PlaywrightTestRunnerServiceImpl implements PlaywrightTestRunnerService {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightTestRunnerServiceImpl.class);

    private final GeneratedTestRepository testRepository;
    private final PageRepository pageRepository;
    private final ExecutionService executionService;

    public PlaywrightTestRunnerServiceImpl(GeneratedTestRepository testRepository,
                                           PageRepository pageRepository,
                                           ExecutionService executionService) {
        this.testRepository = testRepository;
        this.pageRepository = pageRepository;
        this.executionService = executionService;
    }

    @Override
    public List<GeneratedTest> runTestsForAnalysis(UUID analysisId) throws Exception {
        List<GeneratedTest> all = new ArrayList<>();
        var pages = pageRepository.findByAnalysisId(analysisId);
        for (var p : pages) {
            var tests = testRepository.findByPageId(p.getId());
            for (var t : tests) {
                try {
                    executionService.execute(t, analysisId);
                } catch (Exception ex) {
                    log.warn("Test execution failed for test {}: {}", t.getId(), ex.getMessage());
                }
                all.add(t);
            }
        }
        return all;
    }

    @Override
    public List<GeneratedTest> runAllTests() throws Exception {
        List<GeneratedTest> all = new ArrayList<>();
        var tests = testRepository.findAll();
        for (var t : tests) {
            var p = pageRepository.findById(t.getPageId()).orElse(null);
            if (p == null) {
                all.add(t);
                continue;
            }
            try {
                executionService.execute(t, p.getAnalysisId());
            } catch (Exception ex) {
                log.warn("Test execution failed for test {}: {}", t.getId(), ex.getMessage());
            }
            all.add(t);
        }
        return all;
    }
}