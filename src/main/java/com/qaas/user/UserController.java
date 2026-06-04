package com.qaas.user;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
