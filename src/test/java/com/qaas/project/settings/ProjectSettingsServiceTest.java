package com.qaas.project.settings;

import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectService;
import com.qaas.project.entity.Project;
import com.qaas.project.entity.ProjectMember;
import com.qaas.project.repository.ProjectMemberRepository;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsRequest;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization tests for project settings.
 *
 * <p>These drive the real {@link ProjectService} access logic (only its repositories are
 * mocked) so the assertions reflect genuine access decisions rather than stubbed ones.
 *
 * <p>Every user here has global role OWNER — the strongest role the app issues — to prove
 * that a global role alone never grants access to a project the user is not part of.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectSettingsServiceTest {

    private static final String OWNER_EMAIL = "owner@qaas.dev";
    private static final String TESTER_EMAIL = "tester@qaas.dev";
    private static final String VIEWER_EMAIL = "viewer@qaas.dev";
    private static final String OUTSIDER_EMAIL = "outsider@qaas.dev";

    @Mock ProjectSettingsRepository settingsRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository memberRepository;
    @Mock UserRepository userRepository;

    ProjectSettingsService service;

    Project project;
    Project otherProject;
    User owner;
    User tester;
    User viewer;
    User outsider;

    @BeforeEach
    void setUp() {
        owner = user(OWNER_EMAIL);
        tester = user(TESTER_EMAIL);
        viewer = user(VIEWER_EMAIL);
        outsider = user(OUTSIDER_EMAIL);

        project = new Project();
        project.setName("Target project");
        project.setOwnerId(owner.getId());

        // A second project the users above have no relationship with at all.
        otherProject = new Project();
        otherProject.setName("Someone else's project");
        otherProject.setOwnerId(UUID.randomUUID());

        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.findById(otherProject.getId())).thenReturn(Optional.of(otherProject));

        member(project.getId(), tester.getId(), "TESTER");
        member(project.getId(), viewer.getId(), "VIEWER");

        UserService userService = new UserService(userRepository, null);
        ProjectService projectService = new ProjectService(
                projectRepository, memberRepository, userService, userRepository,
                null, null, null, null, null, null);

        service = new ProjectSettingsService(settingsRepository, projectRepository, projectService);
    }

    // ── Read access: owner and members, per their existing permissions ──────────

    @Test
    void ownerCanReadSettings() {
        when(settingsRepository.findByProjectId(project.getId()))
                .thenReturn(Optional.of(settings(project.getId(), 55)));

        SettingsResponse response = service.get(project.getId(), OWNER_EMAIL);

        assertThat(response.maxPages()).isEqualTo(55);
    }

    @Test
    void testerMemberCanReadSettings() {
        when(settingsRepository.findByProjectId(project.getId()))
                .thenReturn(Optional.of(settings(project.getId(), 55)));

        assertThat(service.get(project.getId(), TESTER_EMAIL).maxPages()).isEqualTo(55);
    }

    @Test
    void viewerMemberCanReadSettings() {
        when(settingsRepository.findByProjectId(project.getId()))
                .thenReturn(Optional.of(settings(project.getId(), 55)));

        assertThat(service.get(project.getId(), VIEWER_EMAIL).maxPages()).isEqualTo(55);
    }

    @Test
    void readReturnsDefaultsWhenNoSettingsRowExists() {
        when(settingsRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());

        SettingsResponse response = service.get(project.getId(), VIEWER_EMAIL);

        assertThat(response.maxPages()).isEqualTo(20);
        assertThat(response.authConfigured()).isFalse();
    }

    @Test
    void readNeverExposesTheStoredPassword() {
        ProjectSettings stored = settings(project.getId(), 20);
        stored.setAuthPassword("super-secret");
        when(settingsRepository.findByProjectId(project.getId())).thenReturn(Optional.of(stored));

        SettingsResponse response = service.get(project.getId(), VIEWER_EMAIL);

        assertThat(response.authConfigured()).isTrue();
        assertThat(response.toString()).doesNotContain("super-secret");
    }

    // ── Read access: non-members are refused ───────────────────────────────────

    @Test
    void nonMemberCannotReadSettings() {
        assertThatThrownBy(() -> service.get(project.getId(), OUTSIDER_EMAIL))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    void globalOwnerRoleAloneDoesNotGrantReadAccessToAnotherProject() {
        // `outsider` holds global role OWNER but owns no project and is a member of none.
        assertThat(outsider.getRole()).isEqualTo(Role.OWNER);

        assertThatThrownBy(() -> service.get(otherProject.getId(), OUTSIDER_EMAIL))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    void ownerOfOneProjectCannotReadSettingsOfAnotherProject() {
        assertThatThrownBy(() -> service.get(otherProject.getId(), OWNER_EMAIL))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    void memberOfOneProjectCannotReadSettingsOfAnotherProject() {
        assertThatThrownBy(() -> service.get(otherProject.getId(), TESTER_EMAIL))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    void readOfUnknownProjectIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(projectRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(unknown, OWNER_EMAIL))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    // ── Write access: owner only ───────────────────────────────────────────────

    @Test
    void ownerCanChangeSettings() {
        when(settingsRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());
        when(settingsRepository.save(any(ProjectSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SettingsResponse response = service.save(project.getId(), OWNER_EMAIL,
                new SettingsRequest(42, "https://app.test/login", "crawler", "pw", "/logout"));

        assertThat(response.maxPages()).isEqualTo(42);
        assertThat(response.authUsername()).isEqualTo("crawler");
        assertThat(response.authConfigured()).isTrue();
        verify(settingsRepository).save(any(ProjectSettings.class));
    }

    @Test
    void testerMemberCannotChangeSettings() {
        assertThatThrownBy(() -> service.save(project.getId(), TESTER_EMAIL, request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void viewerMemberCannotChangeSettings() {
        assertThatThrownBy(() -> service.save(project.getId(), VIEWER_EMAIL, request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void nonMemberCannotChangeSettings() {
        assertThatThrownBy(() -> service.save(project.getId(), OUTSIDER_EMAIL, request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void globalOwnerRoleAloneDoesNotGrantWriteAccessToAnotherProject() {
        assertThat(outsider.getRole()).isEqualTo(Role.OWNER);

        assertThatThrownBy(() -> service.save(otherProject.getId(), OUTSIDER_EMAIL, request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void ownerOfOneProjectCannotOverwriteAnotherProjectsCredentials() {
        assertThatThrownBy(() -> service.save(otherProject.getId(), OWNER_EMAIL,
                new SettingsRequest(99, "https://evil.test", "attacker", "pw", null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void memberOfOneProjectCannotChangeAnotherProjectsSettings() {
        assertThatThrownBy(() -> service.save(otherProject.getId(), TESTER_EMAIL, request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void writeOfUnknownProjectIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(projectRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(unknown, OWNER_EMAIL, request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");

        verify(settingsRepository, never()).save(any());
    }

    // ── Write behaviour the authorization change must not regress ──────────────

    @Test
    void blankPasswordKeepsTheExistingOne() {
        ProjectSettings stored = settings(project.getId(), 20);
        stored.setAuthPassword("existing-password");
        when(settingsRepository.findByProjectId(project.getId())).thenReturn(Optional.of(stored));
        when(settingsRepository.save(any(ProjectSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.save(project.getId(), OWNER_EMAIL,
                new SettingsRequest(30, "https://app.test/login", "crawler", "   ", null));

        assertThat(stored.getAuthPassword()).isEqualTo("existing-password");
    }

    @Test
    void nonPositiveMaxPagesFallsBackToTheDefault() {
        when(settingsRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());
        when(settingsRepository.save(any(ProjectSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SettingsResponse response = service.save(project.getId(), OWNER_EMAIL,
                new SettingsRequest(0, null, null, null, null));

        assertThat(response.maxPages()).isEqualTo(20);
    }

    @Test
    void savedSettingsStayBoundToTheirOwnProject() {
        when(settingsRepository.findByProjectId(project.getId())).thenReturn(Optional.empty());
        when(settingsRepository.save(any(ProjectSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.save(project.getId(), OWNER_EMAIL, request());

        verify(settingsRepository).save(org.mockito.ArgumentMatchers
                .argThat(s -> project.getId().equals(s.getProjectId())));
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private User user(String email) {
        // Every user is a global OWNER: the strongest role, so any access granted in these
        // tests must come from project membership rather than from the global role.
        User u = new User(email, "encoded", Role.OWNER);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    private void member(UUID projectId, UUID userId, String role) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setMemberRole(role);
        when(memberRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(m));
    }

    private ProjectSettings settings(UUID projectId, int maxPages) {
        ProjectSettings s = new ProjectSettings();
        s.setProjectId(projectId);
        s.setMaxPages(maxPages);
        return s;
    }

    private SettingsRequest request() {
        return new SettingsRequest(42, "https://app.test/login", "crawler", "pw", "/logout");
    }
}
