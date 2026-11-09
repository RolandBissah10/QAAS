package com.qaas.analysis.controller;

import com.qaas.analysis.AnalysisCancellationRegistry;
import com.qaas.analysis.ProgressEmitterRegistry;
import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.analysis.service.AnalysisPipelineService;
import com.qaas.exception.NotFoundException;
import com.qaas.exception.ServiceUnavailableException;
import com.qaas.project.ProjectService;
import com.qaas.project.entity.Project;
import com.qaas.project.entity.ProjectMember;
import com.qaas.project.repository.ProjectMemberRepository;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.user.Role;
import com.qaas.user.User;
import com.qaas.user.UserRepository;
import com.qaas.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The SSE progress stream previously took no principal, so any authenticated caller could
 * subscribe to any analysis id and watch another project's run. These tests pin the fix.
 *
 * <p>EventSource cannot send an Authorization header, so the browser passes {@code ?token=};
 * {@code JwtAuthenticationFilter} resolves it into the same {@link Authentication} any other
 * request carries, which is what the controller authorizes against here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisProgressAuthorizationTest {

    @Mock AnalysisRepository analysisRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @Mock AnalysisPipelineService pipelineService;
    @Mock ProgressEmitterRegistry emitterRegistry;
    @Mock AnalysisCancellationRegistry cancellationRegistry;

    AnalysisController controller;

    Project project;
    Analysis analysis;
    User owner;
    User viewer;

    @BeforeEach
    void setUp() {
        owner = user("owner@qaas.dev");
        viewer = user("viewer@qaas.dev");
        user("outsider@qaas.dev");

        project = new Project();
        project.setOwnerId(owner.getId());
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

        analysis = new Analysis();
        analysis.setProjectId(project.getId());
        when(analysisRepository.findById(analysis.getId())).thenReturn(Optional.of(analysis));

        ProjectMember m = new ProjectMember();
        m.setProjectId(project.getId());
        m.setUserId(viewer.getId());
        m.setMemberRole("VIEWER");
        when(memberRepository.findByProjectIdAndUserId(project.getId(), viewer.getId()))
                .thenReturn(Optional.of(m));

        when(emitterRegistry.subscribe(any())).thenReturn(new SseEmitter());

        UserService userService = new UserService(userRepository, null);
        ProjectService projectService = new ProjectService(
                projectRepository, memberRepository, userService, userRepository,
                analysisRepository, null, null, null, null, null);

        controller = new AnalysisController(analysisRepository, projectRepository, pipelineService,
                emitterRegistry, userRepository, cancellationRegistry, projectService);
    }

    @Test
    void projectOwnerCanSubscribeToProgress() {
        assertThat(controller.progress(analysis.getId(), auth("owner@qaas.dev"))).isNotNull();

        verify(emitterRegistry).subscribe(analysis.getId());
    }

    /** Reading progress is a read: a VIEWER member is allowed, matching their other permissions. */
    @Test
    void viewerMemberCanSubscribeToProgress() {
        assertThat(controller.progress(analysis.getId(), auth("viewer@qaas.dev"))).isNotNull();

        verify(emitterRegistry).subscribe(analysis.getId());
    }

    /** The core regression: an outsider must not be able to watch another project's run. */
    @Test
    void outsiderCannotSubscribeToProgress() {
        assertThatThrownBy(() -> controller.progress(analysis.getId(), auth("outsider@qaas.dev")))
                .isInstanceOf(NotFoundException.class);

        verify(emitterRegistry, never()).subscribe(any());
    }

    @Test
    void unknownAnalysisIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(analysisRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.progress(unknown, auth("owner@qaas.dev")))
                .isInstanceOf(NotFoundException.class);

        verify(emitterRegistry, never()).subscribe(any());
    }

    @Test
    void rejectedAnalysisDispatchIsMarkedFailedAndReturnsServiceUnavailable() {
        UUID projectId = project.getId();
        doThrow(new TaskRejectedException("queue full"))
                .when(pipelineService).run(any(), any(), anyBoolean());

        assertThatThrownBy(() -> controller.start(projectId, false,
                        new AnalysisController.StartRequest("https://app.example"),
                        auth("owner@qaas.dev")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("capacity is currently full");

        verify(cancellationRegistry).deregister(any());
        verify(analysisRepository).failIfRunning(any(), any());
    }

    private Authentication auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
    }

    private User user(String email) {
        User u = new User(email, "encoded", Role.OWNER);   // strongest global role
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }
}
