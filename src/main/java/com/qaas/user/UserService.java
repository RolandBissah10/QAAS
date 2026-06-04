package com.qaas.user;

import com.qaas.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User currentUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public UserDto updateProfile(String email, UpdateProfileRequest request) {
        User user = currentUser(email);
        user.updateProfile(request.displayName());
        return UserDto.from(user);
    }
}
