package com.qaas.project.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsRequest;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import jakarta.servlet.DispatcherType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the real {@link com.qaas.security.SecurityConfig} filter rules for the settings
 * endpoints.
 *
 * <p>This class exists because a plain {@code @WebMvcTest} does <em>not</em> load the
 * application's own SecurityConfig — Spring Boot substitutes a permissive default chain, so
 * {@link ProjectSettingsControllerTest} proves nothing about the filter layer. Without the
 * explicit {@code @Import} below, a regression in SecurityConfig would go unnoticed.
 *
 * <p>The decisive case is {@link #projectOwnerWithGlobalViewerRoleCanWriteSettings()}: only
 * the first registered user gets global role OWNER (see {@code AuthService.register}), so an
 * ordinary user who creates a project holds global role VIEWER. A global-role matcher on
 * {@code PUT /api/projects/**} therefore returned 403 to a project's own owner, before the
 * per-project check could run.
 */
@WebMvcTest(ProjectSettingsController.class)
@Import({com.qaas.security.SecurityConfig.class, com.qaas.exception.GlobalExceptionHandler.class})
class ProjectSettingsSecurityConfigTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ProjectSettingsService service;
    @MockBean com.qaas.security.JwtService jwtService;

    private static final UUID PROJECT_ID = UUID.randomUUID();

    // ── The filter must not pre-empt the per-project decision ──────────────────

    /** A project's own OWNER normally holds global role VIEWER. They must reach the service. */
    @Test
    @WithMockUser(username = "pm@qaas.dev", roles = "VIEWER")
    void projectOwnerWithGlobalViewerRoleCanWriteSettings() throws Exception {
        when(service.save(eq(PROJECT_ID), eq("pm@qaas.dev"), any())).thenReturn(response());

        mvc.perform(put("/api/projects/{id}/settings", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk());

        verify(service).save(eq(PROJECT_ID), eq("pm@qaas.dev"), any());
    }

    @Test
    @WithMockUser(username = "viewer@qaas.dev", roles = "VIEWER")
    void globalViewerCanReachTheReadEndpoint() throws Exception {
        when(service.get(eq(PROJECT_ID), eq("viewer@qaas.dev"))).thenReturn(response());

        mvc.perform(get("/api/projects/{id}/settings", PROJECT_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "tester@qaas.dev", roles = "TESTER")
    void globalTesterReachesTheServiceWhichThenDecides() throws Exception {
        when(service.save(eq(PROJECT_ID), eq("tester@qaas.dev"), any())).thenReturn(response());

        mvc.perform(put("/api/projects/{id}/settings", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk());
    }

    // ── The filter must still stop unauthenticated callers ─────────────────────

    @Test
    void anonymousReadIsUnauthorized() throws Exception {
        mvc.perform(get("/api/projects/{id}/settings", PROJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousWriteIsUnauthorized() throws Exception {
        mvc.perform(put("/api/projects/{id}/settings", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "viewer@qaas.dev", roles = "VIEWER")
    void asyncAnalysisDispatchDoesNotReapplyInitialRequestRoleRules() throws Exception {
        mvc.perform(get("/api/analysis/{id}/progress", PROJECT_ID)
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ASYNC);
                            return request;
                        }))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(NoResourceFoundException.class));
    }

    @Test
    @WithMockUser(username = "viewer@qaas.dev", roles = "VIEWER")
    void initialAnalysisRequestStillRequiresTesterRole() throws Exception {
        mvc.perform(get("/api/analysis/{id}/progress", PROJECT_ID))
                .andExpect(status().isForbidden());
    }

    private SettingsRequest request() {
        return new SettingsRequest(42, "https://app.test", "crawler", "pw", null);
    }

    private SettingsResponse response() {
        return new SettingsResponse(UUID.randomUUID(), 42, "https://app.test", "crawler",
                true, null, OffsetDateTime.now());
    }
}
