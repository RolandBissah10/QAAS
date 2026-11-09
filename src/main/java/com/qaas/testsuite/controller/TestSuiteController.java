package com.qaas.testsuite.controller;

import com.qaas.common.PagedResponse;
import com.qaas.exception.ServiceUnavailableException;
import com.qaas.testsuite.service.TestSuiteRunWorker;
import com.qaas.testsuite.service.TestSuiteService;
import com.qaas.testsuite.TestSuiteDtos.ReplaceTestsRequest;
import com.qaas.testsuite.TestSuiteDtos.SuiteRequest;
import com.qaas.testsuite.TestSuiteDtos.SuiteResponse;
import com.qaas.testsuite.TestSuiteDtos.SuiteRunResponse;
import com.qaas.testsuite.TestSuiteDtos.SuiteRunSummaryResponse;
import com.qaas.testsuite.TestSuiteDtos.TestSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/projects/{projectId}")
public class TestSuiteController {
    private final TestSuiteService suites;
    private final TestSuiteRunWorker runWorker;

    public TestSuiteController(TestSuiteService suites, TestSuiteRunWorker runWorker) {
        this.suites = suites;
        this.runWorker = runWorker;
    }

    @GetMapping("/tests")
    PagedResponse<TestSummary> tests(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            Authentication auth) {
        var tests = suites.tests(projectId, auth.getName(), page, size);
        return PagedResponse.fromMapped(tests, tests.getContent());
    }

    @PostMapping("/test-suites")
    @ResponseStatus(HttpStatus.CREATED)
    SuiteResponse create(
            @PathVariable UUID projectId,
            @Valid @RequestBody SuiteRequest request,
            Authentication auth) {
        return suites.create(projectId, auth.getName(), request);
    }

    @GetMapping("/test-suites")
    List<SuiteResponse> list(@PathVariable UUID projectId, Authentication auth) {
        return suites.list(projectId, auth.getName());
    }

    @GetMapping("/test-suites/{suiteId}")
    SuiteResponse get(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            Authentication auth) {
        return suites.get(projectId, suiteId, auth.getName());
    }

    @PutMapping("/test-suites/{suiteId}")
    SuiteResponse update(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @Valid @RequestBody SuiteRequest request,
            Authentication auth) {
        return suites.update(projectId, suiteId, auth.getName(), request);
    }

    @DeleteMapping("/test-suites/{suiteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            Authentication auth) {
        suites.delete(projectId, suiteId, auth.getName());
    }

    @PutMapping("/test-suites/{suiteId}/tests")
    SuiteResponse replaceTests(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @Valid @RequestBody ReplaceTestsRequest request,
            Authentication auth) {
        return suites.replaceTests(projectId, suiteId, auth.getName(), request);
    }

    @PostMapping("/test-suites/{suiteId}/runs")
    ResponseEntity<SuiteRunResponse> startRun(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @RequestParam(required = false) UUID environmentId,
            Authentication auth) {
        SuiteRunResponse run = suites.startRun(projectId, suiteId, auth.getName(), environmentId);
        try {
            runWorker.execute(run.id());
        } catch (TaskRejectedException exception) {
            suites.failRun(run.id(), "The background execution queue is full");
            throw new ServiceUnavailableException(
                    "The test suite could not be queued; retry when capacity is available", exception);
        }
        return ResponseEntity.accepted().body(run);
    }

    @GetMapping("/test-suites/{suiteId}/runs")
    PagedResponse<SuiteRunSummaryResponse> listRuns(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth) {
        var runs = suites.listRuns(projectId, suiteId, auth.getName(), page, size);
        return PagedResponse.from(runs);
    }

    @GetMapping("/test-suites/{suiteId}/runs/{runId}")
    SuiteRunResponse getRun(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @PathVariable UUID runId,
            Authentication auth) {
        return suites.getRun(projectId, suiteId, runId, auth.getName());
    }
}
