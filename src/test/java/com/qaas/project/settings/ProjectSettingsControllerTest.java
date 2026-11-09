package com.qaas.project.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaas.exception.NotFoundException;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsRequest;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the settings endpoints.
 *
 * <p>The service-layer tests prove the access <em>decisions</em>; these prove the decisions
 * actually reach HTTP — that the controller forwards the authenticated principal rather than
 * trusting the path, and that a denial surfaces as 404 rather than leaking the resource.
 */
@WebMvcTest(ProjectSettingsController.class)
@Import(com.qaas.exception.GlobalExceptionHandler.class)
class ProjectSettingsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ProjectSettingsService service;

    /** The app's JWT filter is a @Component and gets picked up here; it needs this to build.
     *  No test sends a token, so the filter falls through to @WithMockUser. */
    @MockBean com.qaas.security.JwtService jwtService;

    private static final UUID PROJECT_ID = UUID.randomUUID();

    /** The controller must pass the authenticated email, never anything client-supplied. */
    @Test
    @WithMockUser(username = "owner@qaas.dev", roles = "OWNER")
    void getForwardsTheAuthenticatedPrincipalToTheService() throws Exception {
        when(service.get(eq(PROJECT_ID), eq("owner@qaas.dev"))).thenReturn(response());

        mvc.perform(get("/api/projects/{id}/settings", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPages").value(42));

        verify(service).get(PROJECT_ID, "owner@qaas.dev");
    }

    @Test
    @WithMockUser(username = "owner@qaas.dev", roles = "OWNER")
    void putForwardsTheAuthenticatedPrincipalToTheService() throws Exception {
        when(service.save(eq(PROJECT_ID), eq("owner@qaas.dev"), any())).thenReturn(response());

        mvc.perform(put("/api/projects/{id}/settings", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SettingsRequest(42, "https://app.test", "crawler", "pw", null))))
                .andExpect(status().isOk());

        verify(service).save(eq(PROJECT_ID), eq("owner@qaas.dev"), any());
    }

    /** A denied read must look exactly like a missing project: 404, no resource detail. */
    @Test
    @WithMockUser(username = "outsider@qaas.dev", roles = "OWNER")
    void deniedReadIsReportedAsNotFound() throws Exception {
        when(service.get(eq(PROJECT_ID), eq("outsider@qaas.dev")))
                .thenThrow(new NotFoundException("Project not found"));

        mvc.perform(get("/api/projects/{id}/settings", PROJECT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found"));
    }

    /** A denied write must be a 404 too — and must not reach persistence. */
    @Test
    @WithMockUser(username = "tester@qaas.dev", roles = "TESTER")
    void deniedWriteIsReportedAsNotFound() throws Exception {
        when(service.save(eq(PROJECT_ID), eq("tester@qaas.dev"), any()))
                .thenThrow(new NotFoundException("Project not found"));

        mvc.perform(put("/api/projects/{id}/settings", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SettingsRequest(99, "https://evil.test", "attacker", "pw", null))))
                .andExpect(status().isNotFound());
    }

    /** An unauthenticated caller must never reach the service at all. */
    @Test
    void anonymousCallerIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(get("/api/projects/{id}/settings", PROJECT_ID))
                .andExpect(status().is4xxClientError());

        verify(service, never()).get(any(), any());
    }

    @Test
    void anonymousWriteIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(put("/api/projects/{id}/settings", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SettingsRequest(99, null, null, null, null))))
                .andExpect(status().is4xxClientError());

        verify(service, never()).save(any(), any(), any());
    }

    private SettingsResponse response() {
        return new SettingsResponse(UUID.randomUUID(), 42, "https://app.test", "crawler",
                true, null, OffsetDateTime.now());
    }
}
