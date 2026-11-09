package com.qaas.testsuite.service;

import com.qaas.execution.ExecutionService;
import com.qaas.execution.TestExecution;
import com.qaas.testsuite.TestSuiteDtos.SuiteRunWorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TestSuiteRunWorker {
    private static final Logger log = LoggerFactory.getLogger(TestSuiteRunWorker.class);

    private final TestSuiteService suites;
    private final ExecutionService executions;

    public TestSuiteRunWorker(TestSuiteService suites, ExecutionService executions) {
        this.suites = suites;
        this.executions = executions;
    }

    @Async
    public void execute(UUID runId) {
        try {
            suites.markRunRunning(runId);
            for (UUID itemId : suites.runItemIds(runId)) {
                suites.markItemRunning(itemId);
                SuiteRunWorkItem item = suites.workItem(itemId);
                try {
                    boolean apiTest = item.test().getType() != null
                            && item.test().getType().startsWith("api-");
                    TestExecution execution;
                    if (item.environmentSelected()) {
                        execution = apiTest
                                ? executions.executeApiTestInEnvironment(item.test(), item.analysisId(),
                                        item.targetUrl(), item.authUsername(), item.authPassword())
                                : executions.executeInEnvironment(item.test(), item.analysisId(),
                                        item.targetUrl(), item.authUsername(), item.authPassword());
                    } else {
                        execution = apiTest
                                ? executions.executeApiTest(item.test(), item.analysisId(), null)
                                : executions.execute(item.test(), item.analysisId());
                    }
                    suites.completeItem(itemId, execution);
                } catch (Exception exception) {
                    log.error("Test suite run {} failed to execute test {}",
                            runId, item.test().getId(), exception);
                    suites.failItem(itemId, "Execution error: " + exception.getMessage());
                }
            }
            suites.completeRun(runId);
        } catch (Exception exception) {
            log.error("Test suite run {} failed", runId, exception);
            suites.failRun(runId, "Suite runner error: " + exception.getMessage());
        }
    }
}
