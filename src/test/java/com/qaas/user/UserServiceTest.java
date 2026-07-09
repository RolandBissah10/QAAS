package com.qaas.user;

import com.qaas.exception.BadRequestException;
import com.qaas.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    UserRepository users;

    @InjectMocks
    UserService service;

    @Test
    void listUsersReturnsAllKnownUsers() {
        User user = new User("owner@qaas.dev", "secret", Role.OWNER);
        Pageable pageable = PageRequest.of(0, 20);
        when(users.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user)));

        var result = service.listUsers(pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).email()).isEqualTo("owner@qaas.dev");
    }

    @Test
    void updateUserChangesRoleAndDisplayName() {
        UUID userId = UUID.randomUUID();
        User user = new User("member@qaas.dev", "secret", Role.TESTER);
        when(users.findById(userId)).thenReturn(Optional.of(user));

        var request = new UserManagementDtos.UpdateUserRequest(Role.VIEWER, "Read Only User");
        var result = service.updateUser("owner@qaas.dev", userId, request);

        assertThat(result.role()).isEqualTo(Role.VIEWER);
        assertThat(result.displayName()).isEqualTo("Read Only User");
    }

    @Test
    void deleteUserRejectsSelfDeletion() {
        UUID userId = UUID.randomUUID();
        User user = new User("owner@qaas.dev", "secret", Role.OWNER);
        when(users.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.deleteUser("owner@qaas.dev", userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Owners cannot delete their own account");
    }

    @Test
    void deleteUserRemovesOtherUser() {
        UUID userId = UUID.randomUUID();
        User user = new User("member@qaas.dev", "secret", Role.TESTER);
        when(users.findById(userId)).thenReturn(Optional.of(user));

        service.deleteUser("owner@qaas.dev", userId);

        verify(users).delete(user);
    }

    @Test
    void updateUserThrowsWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUser("owner@qaas.dev", userId, new UserManagementDtos.UpdateUserRequest(Role.VIEWER, "x")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }
}
