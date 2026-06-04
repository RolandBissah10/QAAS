package com.qaas.user;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    UserDto me(Authentication authentication) {
        return UserDto.from(service.currentUser(authentication.getName()));
    }

    @PutMapping("/me")
    UserDto updateMe(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(authentication.getName(), request);
    }

    @GetMapping
    List<UserDto> listUsers() {
        return service.listUsers();
    }

    @PutMapping("/{id}")
    UserDto updateUser(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UserManagementDtos.UpdateUserRequest request
    ) {
        return service.updateUser(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    void removeUser(Authentication authentication, @PathVariable UUID id) {
        service.deleteUser(authentication.getName(), id);
    }
}
