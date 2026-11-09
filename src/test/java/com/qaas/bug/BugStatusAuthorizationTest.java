package com.qaas.bug;

import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.exception.NotFoundException;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
 * {@code PATCH /api/bugs/{id}/status} previously took no principal: any caller whose global
 * role passed the URL matcher could mutate a bug in any project. These tests pin the fix —
 * the bug is authorized through its analysis' project, like every other sub-entity.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BugStatusAuthorizationTest {

    @Mock BugRepository bugs;
    @Mock AnalysisRepository analyses;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository memberRepository;
    @Mock UserRepository userRepository;

    BugController controller;

    Project project;
    Analysis analysis;
    Bug bug;
    User owner;
    User member;
    User outsider;

    @BeforeEach
    void setUp() {
        owner = user("owner@qaas.dev");
        member = user("tester@qaas.dev");
        outsider = user("outsider@qaas.dev");

        project = new Project();
        project.setName("Target");
        project.setOwnerId(owner.getId());
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

        analysis = new Analysis();
        analysis.setProjectId(project.getId());
        when(analyses.findById(analysis.getId())).thenReturn(Optional.of(analysis));

        bug = new Bug(UUID.randomUUID(), analysis.getId(), "Test failure", "boom", Severity.HIGH);
        when(bugs.findById(bug.getId())).thenReturn(Optional.of(bug));
        when(bugs.save(any(Bug.class))).thenAnswer(i -> i.getArgument(0));

        ProjectMember m = new ProjectMember();
        m.setProjectId(project.getId());
        m.setUserId(member.getId());
        m.setMemberRole("TESTER");
        when(memberRepository.findByProjectIdAndUserId(project.getId(), member.getId()))
                .thenReturn(Optional.of(m));

        UserService userService = new UserService(userRepository, null);
        ProjectService projectService = new ProjectService(
                projectRepository, memberRepository, userService, userRepository,
                analyses, null, null, null, null, null);

        controller = new BugController(new BugDetectionService(bugs), projectService);
    }

    @Test
    void projectOwnerCanUpdateBugStatus() {
        controller.updateStatus(bug.getId(), new BugController.StatusRequest(BugStatus.FIXED), auth("owner@qaas.dev"));

        assertThat(bug.getStatus()).isEqualTo(BugStatus.FIXED);
    }

    @Test
    void projectMemberCanUpdateBugStatus() {
        controller.updateStatus(bug.getId(), new BugController.StatusRequest(BugStatus.FIXED), auth("tester@qaas.dev"));

        assertThat(bug.getStatus()).isEqualTo(BugStatus.FIXED);
    }

    /** The core regression: a global role must not let an outsider mutate another project's bug. */
    @Test
    void outsiderCannotUpdateBugStatus() {
        assertThat(outsider.getRole()).isEqualTo(Role.OWNER);   // strongest global role

        assertThatThrownBy(() -> controller.updateStatus(
                bug.getId(), new BugController.StatusRequest(BugStatus.FIXED), auth("outsider@qaas.dev")))
                .isInstanceOf(NotFoundException.class);

        verify(bugs, never()).save(any());
    }

    @Test
    void unknownBugIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(bugs.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.updateStatus(
                unknown, new BugController.StatusRequest(BugStatus.FIXED), auth("owner@qaas.dev")))
                .isInstanceOf(NotFoundException.class);

        verify(bugs, never()).save(any());
    }

    private Authentication auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, java.util.List.of());
    }

    private User user(String email) {
        User u = new User(email, "encoded", Role.OWNER);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }
}
