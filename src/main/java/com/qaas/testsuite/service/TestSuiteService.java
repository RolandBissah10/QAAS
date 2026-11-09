package com.qaas.testsuite.service;

import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.apitest.entity.ApiEndpoint;
import com.qaas.apitest.repository.ApiEndpointRepository;
import com.qaas.environment.EnvironmentDtos.RunEnvironment;
import com.qaas.environment.service.EnvironmentUrlResolver;
import com.qaas.environment.service.TestEnvironmentService;
import com.qaas.exception.BadRequestException;
import com.qaas.exception.ConflictException;
import com.qaas.exception.NotFoundException;
import com.qaas.execution.TestExecution;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.repository.GeneratedTestRepository;
import com.qaas.page.entity.Page;
import com.qaas.page.repository.PageRepository;
import com.qaas.project.ProjectService;
import com.qaas.testsuite.entity.TestSuite;
import com.qaas.testsuite.entity.TestSuiteItemStatus;
import com.qaas.testsuite.entity.TestSuiteMembership;
import com.qaas.testsuite.entity.TestSuiteRun;
import com.qaas.testsuite.entity.TestSuiteRunItem;
import com.qaas.testsuite.entity.TestSuiteRunStatus;
import com.qaas.testsuite.TestSuiteDtos.SuiteRunSummaryResponse;
import com.qaas.testsuite.repository.TestSuiteMembershipRepository;
import com.qaas.testsuite.repository.TestSuiteRepository;
import com.qaas.testsuite.repository.TestSuiteRunItemRepository;
import com.qaas.testsuite.repository.TestSuiteRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static com.qaas.testsuite.TestSuiteDtos.*;

@Service
public class TestSuiteService {
    private final TestSuiteRepository suites;
    private final TestSuiteMembershipRepository memberships;
    private final TestSuiteRunRepository runs;
    private final TestSuiteRunItemRepository runItems;
    private final GeneratedTestRepository tests;
    private final ApiEndpointRepository apiEndpoints;
    private final TestEnvironmentService environments;
    private final AnalysisRepository analyses;
    private final PageRepository pages;
    private final ProjectService projects;

    public TestSuiteService(TestSuiteRepository suites,
                            TestSuiteMembershipRepository memberships,
                            TestSuiteRunRepository runs,
                            TestSuiteRunItemRepository runItems,
                            GeneratedTestRepository tests,
                            ApiEndpointRepository apiEndpoints,
                            TestEnvironmentService environments,
                            AnalysisRepository analyses,
                            PageRepository pages,
                            ProjectService projects) {
        this.suites = suites;
        this.memberships = memberships;
        this.runs = runs;
        this.runItems = runItems;
        this.tests = tests;
        this.apiEndpoints = apiEndpoints;
        this.environments = environments;
        this.analyses = analyses;
        this.pages = pages;
        this.projects = projects;
    }

    @Transactional
    public SuiteResponse create(UUID projectId, String userEmail, SuiteRequest request) {
        authorizeProject(projectId, userEmail);
        TestSuite suite = suites.save(new TestSuite(projectId, request.name().trim(), request.description()));
        return toSuiteResponse(suite);
    }

    @Transactional(readOnly = true)
    public List<SuiteResponse> list(UUID projectId, String userEmail) {
        authorizeProject(projectId, userEmail);
        return suites.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toSuiteResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SuiteResponse get(UUID projectId, UUID suiteId, String userEmail) {
        return toSuiteResponse(requireSuite(projectId, suiteId, userEmail));
    }

    @Transactional
    public SuiteResponse update(UUID projectId, UUID suiteId, String userEmail, SuiteRequest request) {
        TestSuite suite = requireSuite(projectId, suiteId, userEmail);
        suite.update(request.name().trim(), request.description());
        return toSuiteResponse(suites.save(suite));
    }

    @Transactional
    public void delete(UUID projectId, UUID suiteId, String userEmail) {
        TestSuite suite = requireSuite(projectId, suiteId, userEmail);
        projects.getOwned(projectId, userEmail);
        if (runs.existsBySuite_IdAndStatusIn(suiteId,
                List.of(TestSuiteRunStatus.QUEUED, TestSuiteRunStatus.RUNNING))) {
            throw new ConflictException("A test suite cannot be deleted while a run is queued or running");
        }

        runItems.deleteBySuiteId(suiteId);
        runs.deleteBySuiteId(suiteId);
        memberships.deleteBySuiteId(suiteId);
        suites.delete(suite);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TestSummary> tests(
            UUID projectId, String userEmail, int page, int size) {
        authorizeProject(projectId, userEmail);
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return tests.findByProjectId(projectId, pageable).map(TestSummary::from);
    }

    @Transactional
    public SuiteResponse replaceTests(UUID projectId, UUID suiteId, String userEmail,
                                      ReplaceTestsRequest request) {
        TestSuite suite = requireSuite(projectId, suiteId, userEmail);
        LinkedHashSet<UUID> requestedIds = new LinkedHashSet<>(request.testIds());
        if (requestedIds.size() != request.testIds().size()) {
            throw new BadRequestException("A test may only appear once in a suite");
        }

        List<GeneratedTest> selected = tests.findAllById(requestedIds);
        if (selected.size() != requestedIds.size()) {
            throw new BadRequestException("All selected tests must belong to this project");
        }
        selected.forEach(test -> validateTestProject(test, projectId));

        memberships.deleteBySuiteId(suiteId);
        List<TestSuiteMembership> replacements = selected.stream()
                .map(test -> new TestSuiteMembership(suite, test))
                .toList();
        memberships.saveAll(replacements);
        return toSuiteResponse(suite);
    }

    @Transactional
    public SuiteRunResponse startRun(UUID projectId, UUID suiteId, String userEmail, UUID environmentId) {
        TestSuite suite = requireSuite(projectId, suiteId, userEmail);
        List<TestSuiteMembership> suiteTests = memberships.findWithTestsBySuiteId(suiteId);
        if (suiteTests.isEmpty()) {
            throw new BadRequestException("Add at least one test to the suite before running it");
        }

        RunEnvironment environment = environments.findRunEnvironment(projectId, environmentId);
        TestSuiteRun run = runs.saveAndFlush(new TestSuiteRun(suite, suiteTests.size(), environment));
        List<TestSuiteRunItem> items = suiteTests.stream()
                .map(membership -> new TestSuiteRunItem(run, membership.getTest()))
                .toList();
        runItems.saveAll(items);
        return SuiteRunResponse.from(run, items);
    }

    @Transactional(readOnly = true)
    public SuiteRunResponse getRun(UUID projectId, UUID suiteId, UUID runId, String userEmail) {
        TestSuite suite = requireSuite(projectId, suiteId, userEmail);
        TestSuiteRun run = runs.findByIdAndSuite_Id(runId, suite.getId())
                .orElseThrow(() -> new NotFoundException("Test suite run not found"));
        return SuiteRunResponse.from(run, runItems.findByRun_IdOrderByCreatedAt(runId));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SuiteRunSummaryResponse> listRuns(
            UUID projectId, UUID suiteId, String userEmail, int page, int size) {
        TestSuite suite = requireSuite(projectId, suiteId, userEmail);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return runs.findBySuite_Id(suite.getId(), pageable).map(SuiteRunSummaryResponse::from);
    }

    @Transactional
    public void markRunRunning(UUID runId) {
        TestSuiteRun run = runs.findById(runId)
                .orElseThrow(() -> new NotFoundException("Test suite run not found"));
        run.start();
        runs.save(run);
    }

    @Transactional(readOnly = true)
    public List<UUID> runItemIds(UUID runId) {
        return runItems.findIdsByRunId(runId);
    }

    @Transactional
    public void markItemRunning(UUID itemId) {
        TestSuiteRunItem item = runItems.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Test suite run item not found"));
        item.start();
        runItems.save(item);
    }

    @Transactional(readOnly = true)
    public SuiteRunWorkItem workItem(UUID itemId) {
        TestSuiteRunItem item = runItems.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Test suite run item not found"));
        TestSuiteRun run = item.getRun();
        String targetUrl = item.getTest().getTargetUrl();
        if (run.getEnvironmentBaseUrl() != null) {
            targetUrl = EnvironmentUrlResolver.resolve(run.getEnvironmentBaseUrl(), targetUrl);
        }
        return new SuiteRunWorkItem(item.getId(), item.getTest(), analysisIdFor(item.getTest()),
                targetUrl, run.getEnvironmentAuthUsername(), run.getEnvironmentAuthPassword(),
                run.getEnvironmentBaseUrl() != null);
    }

    @Transactional
    public void completeItem(UUID itemId, TestExecution execution) {
        TestSuiteRunItem item = runItems.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Test suite run item not found"));
        item.complete(execution);
        runItems.save(item);
    }

    @Transactional
    public void failItem(UUID itemId, String message) {
        TestSuiteRunItem item = runItems.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Test suite run item not found"));
        item.fail(message);
        runItems.save(item);
    }

    @Transactional
    public void completeRun(UUID runId) {
        TestSuiteRun run = runs.findById(runId)
                .orElseThrow(() -> new NotFoundException("Test suite run not found"));
        int passed = itemCount(runId, TestSuiteItemStatus.PASSED);
        int failed = itemCount(runId, TestSuiteItemStatus.FAILED);
        int errors = itemCount(runId, TestSuiteItemStatus.ERROR);
        run.complete(passed, failed, errors);
        runs.save(run);
    }

    @Transactional
    public void failRun(UUID runId, String message) {
        TestSuiteRun run = runs.findById(runId)
                .orElseThrow(() -> new NotFoundException("Test suite run not found"));
        for (TestSuiteRunItem item : runItems.findByRun_IdOrderByCreatedAt(runId)) {
            if (item.getStatus() == TestSuiteItemStatus.QUEUED
                    || item.getStatus() == TestSuiteItemStatus.RUNNING) {
                item.fail(message);
                runItems.save(item);
            }
        }
        run.complete(itemCount(runId, TestSuiteItemStatus.PASSED),
                itemCount(runId, TestSuiteItemStatus.FAILED),
                itemCount(runId, TestSuiteItemStatus.ERROR));
        runs.save(run);
    }

    private int itemCount(UUID runId, TestSuiteItemStatus status) {
        return Math.toIntExact(runItems.countByRun_IdAndStatus(runId, status));
    }

    private void authorizeProject(UUID projectId, String userEmail) {
        projects.find(projectId, userEmail);
    }

    private TestSuite requireSuite(UUID projectId, UUID suiteId, String userEmail) {
        authorizeProject(projectId, userEmail);
        return suites.findByIdAndProjectId(suiteId, projectId)
                .orElseThrow(() -> new NotFoundException("Test suite not found"));
    }

    private SuiteResponse toSuiteResponse(TestSuite suite) {
        return SuiteResponse.from(suite, memberships.findWithTestsBySuiteId(suite.getId()));
    }

    private void validateTestProject(GeneratedTest test, UUID projectId) {
        Analysis analysis = analyses.findById(analysisIdFor(test))
                .orElseThrow(() -> new BadRequestException("Selected test has no valid analysis"));
        if (!projectId.equals(analysis.getProjectId())) {
            throw new BadRequestException("All selected tests must belong to this project");
        }
        if (test.getType() != null && test.getType().startsWith("api-")) {
            ApiEndpoint endpoint = test.getApiEndpointId() == null
                    ? null : apiEndpoints.findById(test.getApiEndpointId()).orElse(null);
            if (endpoint == null || !analysis.getId().equals(endpoint.getAnalysisId())) {
                throw new BadRequestException("API tests must reference an endpoint from their analysis");
            }
        }
    }

    private UUID analysisIdFor(GeneratedTest test) {
        if (test.getAnalysisId() != null) {
            return test.getAnalysisId();
        }
        UUID pageId = test.getPageId();
        if (pageId != null) {
            Page page = pages.findById(pageId)
                    .orElseThrow(() -> new BadRequestException("Selected test has no valid page"));
            return page.getAnalysisId();
        }
        throw new BadRequestException("Selected test is not linked to an analysis");
    }
}
