package com.qaas.project.settings;

import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectService;
import com.qaas.project.entity.Project;
import com.qaas.project.entity.ProjectMember;
import com.qaas.project.repository.ProjectMemberRepository;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsRequest;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Adversarial edge cases for project-settings authorization.
 *
 * <p>{@link ProjectSettingsServiceTest} covers the ordinary role matrix. This class exists
 * to prove the checks <em>fail closed</em> on malformed or hostile data rather than
 * accidentally granting access — the cases where an authorization bug usually hides.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectSettingsAuthorizationEdgeCaseTest {

    @Mock ProjectSettingsRepository settingsRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository memberRepository;
    @Mock UserRepository userRepository;

    ProjectSettingsService service;
    ProjectService projectService;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, null);
        projectService = new ProjectService(
                projectRepository, memberRepository, userService, userRepository,
                null, null, null, null, null, null);
        service = new ProjectSettingsService(settingsRepository, projectRepository, projectService);
    }

    /**
     * A project whose ownerId is null must not be readable by everyone. If the check were
     * written as {@code project.getOwnerId().equals(user.getId())} it would NPE, and if it
     * were written loosely it could match a null-id user.
     */
    @Test
    void projectWithNullOwnerIdIsNotReadableByANonMember() {
        user("stranger@qaas.dev");
        Project orphan = new Project();
        orphan.setName("Orphaned project");
        orphan.setOwnerId(null);
        when(projectRepository.findById(orphan.getId())).thenReturn(Optional.of(orphan));

        assertThatThrownBy(() -> service.get(orphan.getId(), "stranger@qaas.dev"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void projectWithNullOwnerIdIsNotWritableByANonMember() {
        user("stranger@qaas.dev");
        Project orphan = new Project();
        orphan.setOwnerId(null);
        when(projectRepository.findById(orphan.getId())).thenReturn(Optional.of(orphan));

        assertThatThrownBy(() -> service.save(orphan.getId(), "stranger@qaas.dev", request()))
                .isInstanceOf(NotFoundException.class);

        verify(settingsRepository, never()).save(any());
    }

    /**
     * An authenticated principal whose email no longer resolves to a user (deleted account,
     * forged-but-valid JWT) must be refused, not treated as anonymous-with-access.
     */
    @Test
    void unknownPrincipalCannotRead() {
        Project project = ownedProject(UUID.randomUUID());
        when(userRepository.findByEmail("ghost@qaas.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(project.getId(), "ghost@qaas.dev"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownPrincipalCannotWrite() {
        Project project = ownedProject(UUID.randomUUID());
        when(userRepository.findByEmail("ghost@qaas.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(project.getId(), "ghost@qaas.dev", request()))
                .isInstanceOf(NotFoundException.class);

        verify(settingsRepository, never()).save(any());
    }

    /**
     * Membership is keyed on (projectId, userId). A membership row belonging to a DIFFERENT
     * project must never authorize this one — this is the cross-project leak in table form.
     */
    @Test
    void membershipOfAnotherProjectDoesNotAuthorizeThisProject() {
        User user = user("member@qaas.dev");
        Project target = ownedProject(UUID.randomUUID());
        UUID otherProjectId = UUID.randomUUID();

        // The user is a TESTER, but of some other project entirely.
        ProjectMember elsewhere = new ProjectMember();
        elsewhere.setProjectId(otherProjectId);
        elsewhere.setUserId(user.getId());
        elsewhere.setMemberRole("TESTER");
        when(memberRepository.findByProjectIdAndUserId(otherProjectId, user.getId()))
                .thenReturn(Optional.of(elsewhere));
        // No row for the target project.
        when(memberRepository.findByProjectIdAndUserId(target.getId(), user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(target.getId(), "member@qaas.dev"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.save(target.getId(), "member@qaas.dev", request()))
                .isInstanceOf(NotFoundException.class);

        verify(settingsRepository, never()).save(any());
    }

    /**
     * A membership row with an unrecognised or tampered role string must not be upgraded to
     * write access. Reads are allowed (any member may read), writes must not be.
     */
    @Test
    void unrecognisedMemberRoleGrantsNoWriteAccess() {
        User user = user("odd@qaas.dev");
        Project target = ownedProject(UUID.randomUUID());

        ProjectMember weird = new ProjectMember();
        weird.setProjectId(target.getId());
        weird.setUserId(user.getId());
        weird.setMemberRole("OWNER");   // not a legal member role; must not confer ownership
        when(memberRepository.findByProjectIdAndUserId(target.getId(), user.getId()))
                .thenReturn(Optional.of(weird));

        assertThatThrownBy(() -> service.save(target.getId(), "odd@qaas.dev", request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");

        verify(settingsRepository, never()).save(any());
    }

    /**
     * Denial must be indistinguishable from a missing project: same exception, same message.
     * A different status or wording would let a caller enumerate projects they cannot see.
     */
    @Test
    void denialIsIndistinguishableFromAMissingProject() {
        user("stranger@qaas.dev");
        Project existing = ownedProject(UUID.randomUUID());
        UUID missing = UUID.randomUUID();
        when(projectRepository.findById(missing)).thenReturn(Optional.empty());

        Throwable denied = org.assertj.core.api.Assertions
                .catchThrowable(() -> service.get(existing.getId(), "stranger@qaas.dev"));
        Throwable absent = org.assertj.core.api.Assertions
                .catchThrowable(() -> service.get(missing, "stranger@qaas.dev"));

        org.assertj.core.api.Assertions.assertThat(denied)
                .isInstanceOf(NotFoundException.class)
                .hasMessage(absent.getMessage());
        org.assertj.core.api.Assertions.assertThat(denied.getClass()).isEqualTo(absent.getClass());
    }

    /**
     * Authorization must happen before any mutation: a rejected write must not have touched
     * the settings repository at all, not even a read-modify of an existing row.
     */
    @Test
    void rejectedWriteNeverReadsOrWritesSettings() {
        user("stranger@qaas.dev");
        Project target = ownedProject(UUID.randomUUID());

        assertThatThrownBy(() -> service.save(target.getId(), "stranger@qaas.dev", request()))
                .isInstanceOf(NotFoundException.class);

        verify(settingsRepository, never()).save(any());
        verify(settingsRepository, never()).findByProjectId(any());
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private User user(String email) {
        User u = new User(email, "encoded", Role.OWNER);   // strongest global role
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    private Project ownedProject(UUID ownerId) {
        Project p = new Project();
        p.setName("Target");
        p.setOwnerId(ownerId);
        when(projectRepository.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private SettingsRequest request() {
        return new SettingsRequest(42, "https://evil.test", "attacker", "pw", null);
    }
}
