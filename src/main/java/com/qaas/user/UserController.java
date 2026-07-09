package com.qaas.user;

import com.qaas.common.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    PagedResponse<UserDto> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listUsers(PageRequest.of(page, size, Sort.by("createdAt").ascending()));
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
