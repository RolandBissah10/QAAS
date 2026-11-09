package com.qaas.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaas.ai.ClaudeClient;
import com.qaas.ai.HtmlSummarizer;
import com.qaas.discovery.repository.UIElementRepository;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.repository.GeneratedTestRepository;
import com.qaas.page.entity.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTestGenerationServiceTest {

    @Test
    void persistsEachGeneratedScenarioAsAnIndependentScriptedTest() throws Exception {
        ClaudeClient client = mock(ClaudeClient.class);
        UIElementRepository uiElements = mock(UIElementRepository.class);
        GeneratedTestRepository repository = mock(GeneratedTestRepository.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString())).thenReturn("""
                [
                  {"name":"Required fields are validated","steps":[{"action":"click","selector":"button"}]},
                  {"name":"Login form submits","steps":[{"action":"fill","selector":"input[type=email]","value":"test@example.com"},{"action":"assertVisible","selector":"form"}]}
                ]
                """);
        when(uiElements.findByPageId(any())).thenReturn(List.of());
        when(repository.save(any(GeneratedTest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Page page = new Page();
        page.setUrl("https://app.example.test/login");
        page.setPageType("LOGIN");
        AiTestGenerationService service = new AiTestGenerationService(
                client, new HtmlSummarizer(), repository, uiElements, new ObjectMapper());

        List<GeneratedTest> tests = service.generateForPage(
                page, "<html><body><form><input type='email'><button>Login</button></form></body></html>");

        assertThat(tests).hasSize(2);
        assertThat(tests).extracting(GeneratedTest::getName)
                .containsExactly(
                        "AI: Required fields are validated — https://app.example.test/login",
                        "AI: Login form submits — https://app.example.test/login");
        assertThat(tests.get(1).getScriptJson())
                .isEqualTo("[{\"action\":\"fill\",\"selector\":\"input[type=email]\",\"value\":\"test@example.com\"},{\"action\":\"assertVisible\",\"selector\":\"form\"}]");
    }

    @Test
    void remainsCompatibleWithLegacySingleScenarioStepArrays() throws Exception {
        ClaudeClient client = mock(ClaudeClient.class);
        UIElementRepository uiElements = mock(UIElementRepository.class);
        GeneratedTestRepository repository = mock(GeneratedTestRepository.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString())).thenReturn(
                "[{\"action\":\"assertVisible\",\"selector\":\"form\"}]");
        when(uiElements.findByPageId(any())).thenReturn(List.of());
        when(repository.save(any(GeneratedTest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Page page = new Page();
        page.setUrl("https://app.example.test/");
        AiTestGenerationService service = new AiTestGenerationService(
                client, new HtmlSummarizer(), repository, uiElements, new ObjectMapper());

        List<GeneratedTest> tests = service.generateForPage(page, "<html><body><form></form></body></html>");

        assertThat(tests).hasSize(1);
        assertThat(tests.get(0).getScriptJson())
                .isEqualTo("[{\"action\":\"assertVisible\",\"selector\":\"form\"}]");
    }

    @Test
    void createsAuthenticatedJourneyScriptsBoundToDiscoveredPages() throws Exception {
        Page home = new Page();
        home.setUrl("https://app.example.test/");
        home.setTitle("Home");
        Page alerts = new Page();
        alerts.setUrl("https://app.example.test/alerts");
        alerts.setTitle("Alerts");
        List<Page> pages = List.of(home, alerts);

        ClaudeClient client = mock(ClaudeClient.class);
        UIElementRepository uiElements = mock(UIElementRepository.class);
        GeneratedTestRepository repository = mock(GeneratedTestRepository.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            assertThat(prompt).contains("Authenticated crawl state is available: true",
                    "https://app.example.test/alerts", "Create alert");
            return """
                    [
                      {"name":"Create an alert","startUrl":"https://app.example.test/",
                       "steps":[{"action":"click","selector":"a[href='/alerts']"},
                                {"action":"assertVisible","selector":"form"}]}
                    ]
                    """;
        });
        com.qaas.discovery.entity.UIElement link = new com.qaas.discovery.entity.UIElement();
        link.setPageId(home.getId());
        link.setElementType("a");
        link.setSelector("a[href='/alerts']");
        link.setLabel("Create alert");
        when(uiElements.findByPageId(home.getId())).thenReturn(List.of(link));
        when(uiElements.findByPageId(alerts.getId())).thenReturn(List.of());
        when(repository.save(any(GeneratedTest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AiTestGenerationService service = new AiTestGenerationService(
                client, new HtmlSummarizer(), repository, uiElements, new ObjectMapper());

        List<GeneratedTest> journeys = service.generateJourneys(
                pages, "https://app.example.test/", true);

        assertThat(journeys).hasSize(1);
        assertThat(journeys.get(0).getPageId()).isEqualTo(home.getId());
        assertThat(journeys.get(0).getTargetUrl()).isEqualTo(home.getUrl());
        assertThat(journeys.get(0).getName()).isEqualTo("AI Journey: Create an alert");
        assertThat(journeys.get(0).getScriptJson())
                .contains("\"allowedUrls\":[\"https://app.example.test/\",\"https://app.example.test/alerts\"]")
                .contains("\"action\":\"click\"");
    }

    @Test
    void rejectsJourneysThatStartOrNavigateToUndiscoveredPages() throws Exception {
        ClaudeClient client = mock(ClaudeClient.class);
        UIElementRepository uiElements = mock(UIElementRepository.class);
        GeneratedTestRepository repository = mock(GeneratedTestRepository.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString())).thenReturn("""
                [
                  {"name":"Known workflow","startUrl":"https://app.example.test/",
                   "steps":[{"action":"navigate","value":"https://app.example.test/alerts"}]},
                  {"name":"Unknown route","startUrl":"https://app.example.test/admin",
                   "steps":[{"action":"assertVisible","selector":"body"}]},
                  {"name":"Unknown navigation","startUrl":"https://app.example.test/",
                   "steps":[{"action":"navigate","value":"https://app.example.test/admin"}]}
                ]
                """);
        when(uiElements.findByPageId(any())).thenReturn(List.of());
        when(repository.save(any(GeneratedTest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Page home = new Page();
        home.setUrl("https://app.example.test/");
        Page alerts = new Page();
        alerts.setUrl("https://app.example.test/alerts");
        AiTestGenerationService service = new AiTestGenerationService(
                client, new HtmlSummarizer(), repository, uiElements, new ObjectMapper());

        List<GeneratedTest> journeys = service.generateJourneys(
                List.of(home, alerts), home.getUrl(), false);

        assertThat(journeys).hasSize(1);
        assertThat(journeys.get(0).getName()).isEqualTo("AI Journey: Known workflow");
    }
}
