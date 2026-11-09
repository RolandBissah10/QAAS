package com.qaas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.apitest.entity.ApiEndpoint;
import com.qaas.apitest.repository.ApiEndpointRepository;
import com.qaas.execution.ExecutionService;
import com.qaas.execution.ExecutionStatus;
import com.qaas.execution.TestExecutionRepository;
import com.qaas.environment.EnvironmentDtos.EnvironmentRequest;
import com.qaas.environment.EnvironmentDtos.EnvironmentResponse;
import com.qaas.environment.service.TestEnvironmentService;
import com.qaas.exception.BadRequestException;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.repository.GeneratedTestRepository;
import com.qaas.playwright.service.PlaywrightService;
import com.qaas.project.entity.Project;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.testsuite.service.TestSuiteRunWorker;
import com.qaas.testsuite.service.TestSuiteService;
import com.qaas.testsuite.TestSuiteDtos.ReplaceTestsRequest;
import com.qaas.testsuite.TestSuiteDtos.SuiteRequest;
import com.qaas.testsuite.TestSuiteDtos.SuiteRunResponse;
import com.qaas.testsuite.TestSuiteDtos.SuiteResponse;
import com.qaas.testsuite.TestSuiteDtos.SuiteRunSummaryResponse;
import com.qaas.testsuite.TestSuiteDtos.TestSummary;
import com.qaas.testsuite.entity.TestSuiteRunStatus;
import com.qaas.user.Role;
import com.qaas.user.User;
import com.qaas.user.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class QaasApplicationIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("qaas")
            .withUsername("qaas")
            .withPassword("qaas");

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AnalysisRepository analyses;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ExecutionService executionService;
    @Autowired GeneratedTestRepository generatedTests;
    @Autowired TestExecutionRepository executions;
    @Autowired ApiEndpointRepository apiEndpoints;
    @Autowired TestSuiteService testSuites;
    @Autowired TestSuiteRunWorker testSuiteRunWorker;
    @Autowired TestEnvironmentService testEnvironmentService;
    @MockBean PlaywrightService screenshotService;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("qaas.jwt.secret", () -> "integration-test-secret-with-32-chars");
        registry.add("qaas.crypto.secret", () -> "integration-test-crypto-secret-at-least-32-chars");
    }

    @Test
    void contextLoads() {
    }

    @Test
    void freshDatabaseIsCreatedByFlywayAndValidatedByHibernate() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");

        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class))
                .contains("users", "analyses", "project_settings", "refresh_tokens", "recording_entries",
                        "test_suites", "test_suite_memberships", "test_suite_runs", "test_suite_run_items",
                        "test_environments");
    }

    @Test
    void existingNonEmptySchemaIsBaselinedWithoutReplayingInitialMigration() {
        jdbcTemplate.execute("CREATE SCHEMA legacy_baseline_probe");
        jdbcTemplate.execute("CREATE TABLE legacy_baseline_probe.existing_table (id integer)");
        jdbcTemplate.execute("CREATE TABLE legacy_baseline_probe.projects (id uuid PRIMARY KEY)");
        jdbcTemplate.execute("CREATE TABLE legacy_baseline_probe.generated_tests (id uuid PRIMARY KEY)");
        jdbcTemplate.execute("CREATE TABLE legacy_baseline_probe.executions (id uuid PRIMARY KEY)");
        try {
            Flyway legacySchemaFlyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas("legacy_baseline_probe")
                    .defaultSchema("legacy_baseline_probe")
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .locations("classpath:db/migration")
                    .load();

            legacySchemaFlyway.migrate();

            assertThat(legacySchemaFlyway.info().current().getVersion().getVersion()).isEqualTo("3");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT to_regclass('legacy_baseline_probe.users')", String.class))
                    .isNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT to_regclass('legacy_baseline_probe.test_suites')", String.class))
                    .isEqualTo("legacy_baseline_probe.test_suites");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT to_regclass('legacy_baseline_probe.test_environments')", String.class))
                    .isEqualTo("legacy_baseline_probe.test_environments");
        } finally {
            jdbcTemplate.execute("DROP SCHEMA legacy_baseline_probe CASCADE");
        }
    }

    @Test
    void postgresPersistsAnalysisStateTransitions() {
        Analysis failed = runningAnalysis();
        analyses.saveAndFlush(failed);

        assertThat(analyses.failIfRunning(failed.getId(), OffsetDateTime.now())).isEqualTo(1);
        assertThat(analyses.findById(failed.getId()).orElseThrow().getStatus()).isEqualTo("FAILED");

        Analysis cancelled = runningAnalysis();
        analyses.saveAndFlush(cancelled);
        assertThat(analyses.cancelIfRunning(cancelled.getId(), OffsetDateTime.now())).isEqualTo(1);
        assertThat(analyses.findById(cancelled.getId()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @WithMockUser(username = "pipeline-owner@qaas.test", roles = "OWNER")
    void analysisStartPersistsAsyncPipelineFailureThroughTheApi() throws Exception {
        User owner = users.saveAndFlush(new User("pipeline-owner@qaas.test", "encoded", Role.OWNER));
        Project project = new Project();
        project.setName("pipeline integration test");
        project.setOwnerId(owner.getId());
        projects.saveAndFlush(project);

        String response = mvc.perform(post("/api/analysis/start")
                        .queryParam("projectId", project.getId().toString())
                        .contentType("application/json")
                        .content("{\"url\":\"file:///invalid-target\"}"))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID analysisId = UUID.fromString(
                objectMapper.readTree(response).path("id").asText());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(analyses.findById(analysisId).orElseThrow().getStatus()).isEqualTo("FAILED"));
    }

    @Test
    void scriptedBrowserExecutionPassesAndPersistsInPostgres() throws Exception {
        Analysis analysis = runningAnalysis();
        analyses.saveAndFlush(analysis);

        GeneratedTest generatedTest = new GeneratedTest();
        generatedTest.setAnalysisId(analysis.getId());
        generatedTest.setName("Greets the submitted user");
        generatedTest.setType("scripted");
        generatedTest.setTargetUrl("https://qaas.test/");
        generatedTest.setScriptJson("""
                [
                  {"action":"fill","selector":"#name","value":"QAAS"},
                  {"action":"click","selector":"#submit"},
                  {"action":"assertText","selector":"#result","contains":"Hello, QAAS"}
                ]
                """);
        generatedTests.saveAndFlush(generatedTest);

        try (com.microsoft.playwright.Playwright playwright = com.microsoft.playwright.Playwright.create()) {
            var launchOptions = new com.microsoft.playwright.BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setChromiumSandbox(false)
                    .setArgs(java.util.List.of("--disable-dev-shm-usage", "--no-first-run"));
            String executablePath = System.getProperty("playwright.chromium.path",
                    System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH"));
            if (executablePath != null && !executablePath.isBlank()) {
                launchOptions.setExecutablePath(java.nio.file.Path.of(executablePath));
            }
            var browser = playwright.chromium().launch(launchOptions);
            try {
                var context = browser.newContext();
                context.route("https://qaas.test/**", route -> route.fulfill(
                        new com.microsoft.playwright.Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("""
                                        <!doctype html>
                                        <html>
                                          <body>
                                            <input id="name">
                                            <button id="submit" onclick="document.querySelector('#result').textContent = 'Hello, ' + document.querySelector('#name').value">Submit</button>
                                            <p id="result"></p>
                                          </body>
                                        </html>
                                        """)));

                var result = executionService.execute(generatedTest, analysis.getId(), context);

                assertThat(result.getStatus()).isEqualTo(ExecutionStatus.PASSED);
                assertThat(executions.findById(result.getId()))
                        .hasValueSatisfying(persisted ->
                                assertThat(persisted.getStatus()).isEqualTo(ExecutionStatus.PASSED));
                verify(screenshotService).captureScreenshot(analysis.getId(), "https://qaas.test/");
                context.close();
            } finally {
                browser.close();
            }
        }
    }

    @Test
    void projectSuiteRunsAsynchronouslyAndPersistsAggregatedApiResults() throws Exception {
        String ownerEmail = "suite-owner-" + UUID.randomUUID() + "@qaas.test";
        User owner = users.saveAndFlush(new User(ownerEmail, "encoded", Role.OWNER));
        Project project = new Project();
        project.setName("suite integration test");
        project.setOwnerId(owner.getId());
        projects.saveAndFlush(project);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        try {
            Analysis analysis = runningAnalysis();
            analysis.setProjectId(project.getId());
            analyses.saveAndFlush(analysis);

            ApiEndpoint endpoint = apiEndpoints.saveAndFlush(new ApiEndpoint(
                    analysis.getId(), "GET", "http://127.0.0.1:" + server.getAddress().getPort() + "/auth", 401));
            GeneratedTest generatedTest = new GeneratedTest();
            generatedTest.setAnalysisId(analysis.getId());
            generatedTest.setApiEndpointId(endpoint.getId());
            generatedTest.setName("Rejects anonymous API access");
            generatedTest.setType("api-auth");
            generatedTest.setTargetUrl(endpoint.getUrl());
            generatedTests.saveAndFlush(generatedTest);

            SuiteResponse suite = testSuites.create(
                    project.getId(), ownerEmail, new SuiteRequest("Authentication checks", null));
            EnvironmentResponse environment = testEnvironmentService.create(project.getId(), ownerEmail,
                    new EnvironmentRequest("Staging", "https://8.8.8.8", "suite-test-user",
                            "suite-test-password", false));
            assertThat(environment.credentialsConfigured()).isTrue();
            assertThat(objectMapper.writeValueAsString(environment)).doesNotContain("suite-test-password");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT auth_password FROM test_environments WHERE id = ?",
                    String.class, environment.id()))
                    .startsWith("enc:v1:");
            assertThat(testSuites.tests(project.getId(), ownerEmail, 0, 20).getContent())
                    .extracting(TestSummary::id)
                    .contains(generatedTest.getId());
            testSuites.replaceTests(project.getId(), suite.id(), ownerEmail,
                    new ReplaceTestsRequest(List.of(generatedTest.getId())));
            SuiteRunResponse environmentRun = testSuites.startRun(
                    project.getId(), suite.id(), ownerEmail, environment.id());
            assertThat(environmentRun.environmentName()).isEqualTo("Staging");
            assertThat(testSuites.workItem(environmentRun.items().get(0).id()))
                    .satisfies(item -> {
                        assertThat(item.environmentSelected()).isTrue();
                        assertThat(item.targetUrl()).isEqualTo(
                                "https://8.8.8.8/auth");
                    });
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT environment_auth_password FROM test_suite_runs WHERE id = ?",
                    String.class, environmentRun.id()))
                    .startsWith("enc:v1:");
            assertThat(objectMapper.writeValueAsString(environmentRun))
                    .doesNotContain("suite-test-password");
            assertThatThrownBy(() -> testEnvironmentService.update(project.getId(), environment.id(), ownerEmail,
                    new EnvironmentRequest("Staging", "https://8.8.8.8", null, null, false)))
                    .isInstanceOf(BadRequestException.class);
            EnvironmentResponse clearedEnvironment = testEnvironmentService.update(
                    project.getId(), environment.id(), ownerEmail,
                    new EnvironmentRequest("Staging", "https://8.8.8.8", null, null, true));
            assertThat(clearedEnvironment.credentialsConfigured()).isFalse();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT environment_auth_password FROM test_suite_runs WHERE id = ?",
                    String.class, environmentRun.id()))
                    .startsWith("enc:v1:");

            Analysis otherAnalysis = runningAnalysis();
            Project otherProject = new Project();
            otherProject.setName("other suite project");
            otherProject.setOwnerId(owner.getId());
            projects.saveAndFlush(otherProject);
            otherAnalysis.setProjectId(otherProject.getId());
            analyses.saveAndFlush(otherAnalysis);
            GeneratedTest outsideTest = new GeneratedTest();
            outsideTest.setAnalysisId(otherAnalysis.getId());
            outsideTest.setName("Other project test");
            outsideTest.setType("smoke");
            generatedTests.saveAndFlush(outsideTest);
            assertThatThrownBy(() -> testSuites.replaceTests(project.getId(), suite.id(), ownerEmail,
                    new ReplaceTestsRequest(List.of(outsideTest.getId()))))
                    .isInstanceOf(BadRequestException.class);

            SuiteRunResponse queued = testSuites.startRun(project.getId(), suite.id(), ownerEmail, null);
            testSuiteRunWorker.execute(queued.id());

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                SuiteRunResponse result = testSuites.getRun(
                        project.getId(), suite.id(), queued.id(), ownerEmail);
                assertThat(result.status()).isEqualTo(TestSuiteRunStatus.PASSED);
                assertThat(result.totalTests()).isEqualTo(1);
                assertThat(result.passedTests()).isEqualTo(1);
                assertThat(result.items()).singleElement()
                        .satisfies(item -> assertThat(item.status()).isEqualTo(
                                com.qaas.testsuite.entity.TestSuiteItemStatus.PASSED));
                assertThat(testSuites.listRuns(project.getId(), suite.id(), ownerEmail, 0, 20)
                        .getContent()).hasSize(2)
                        .extracting(SuiteRunSummaryResponse::id)
                        .contains(queued.id(), environmentRun.id());
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deletingProjectSuiteRemovesHistoryAndRejectsActiveRuns() {
        String ownerEmail = "suite-delete-owner-" + UUID.randomUUID() + "@qaas.test";
        User owner = users.saveAndFlush(new User(ownerEmail, "encoded", Role.OWNER));
        Project project = new Project();
        project.setName("suite deletion integration test");
        project.setOwnerId(owner.getId());
        projects.saveAndFlush(project);

        Analysis analysis = runningAnalysis();
        analysis.setProjectId(project.getId());
        analyses.saveAndFlush(analysis);
        GeneratedTest generatedTest = new GeneratedTest();
        generatedTest.setAnalysisId(analysis.getId());
        generatedTest.setName("Deletion test");
        generatedTest.setType("smoke");
        generatedTests.saveAndFlush(generatedTest);

        SuiteResponse suite = testSuites.create(
                project.getId(), ownerEmail, new SuiteRequest("Delete me", null));
        testSuites.replaceTests(project.getId(), suite.id(), ownerEmail,
                new ReplaceTestsRequest(List.of(generatedTest.getId())));
        SuiteRunResponse queuedRun = testSuites.startRun(project.getId(), suite.id(), ownerEmail, null);

        assertThatThrownBy(() -> testSuites.delete(project.getId(), suite.id(), ownerEmail))
                .isInstanceOf(com.qaas.exception.ConflictException.class);

        testSuites.failRun(queuedRun.id(), "Test run cleanup");
        testSuites.delete(project.getId(), suite.id(), ownerEmail);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM test_suites WHERE id = ?", Long.class, suite.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM test_suite_memberships WHERE suite_id = ?", Long.class, suite.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM test_suite_runs WHERE suite_id = ?", Long.class, suite.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM test_suite_run_items WHERE run_id = ?", Long.class, queuedRun.id())).isZero();
        assertThat(generatedTests.findById(generatedTest.getId())).isPresent();
    }

    private Analysis runningAnalysis() {
        Analysis analysis = new Analysis();
        analysis.setUrl("https://example.test");
        analysis.setStatus("RUNNING");
        analysis.setStartedAt(OffsetDateTime.now());
        return analysis;
    }
}
