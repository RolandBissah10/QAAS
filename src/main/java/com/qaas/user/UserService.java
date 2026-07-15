package com.qaas.user;

import com.qaas.auth.repository.RefreshTokenRepository;
import com.qaas.common.PagedResponse;
import com.qaas.exception.BadRequestException;
import com.qaas.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens) {
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(readOnly = true)
    public User currentUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserDto> listUsers(Pageable pageable) {
        try {
            log.debug("listUsers invoked");
            var paged = users.findAll(pageable);
            return PagedResponse.fromMapped(paged, paged.getContent().stream()
                    .map(UserDto::from).toList());
        } catch (Exception ex) {
            log.error("Unexpected error in listUsers", ex);
            throw ex;
        }
    }

    @Transactional
    public UserDto updateProfile(String email, UpdateProfileRequest request) {
        User user = currentUser(email);
        user.updateProfile(request.displayName());
        return UserDto.from(user);
    }

    @Transactional
    public UserDto updateUser(String editorEmail, UUID id, UserManagementDtos.UpdateUserRequest request) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        user.updateRole(request.role());
        user.updateProfile(request.displayName());
        return UserDto.from(user);
    }

    @Transactional
    public void deleteUser(String editorEmail, UUID id) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getEmail().equals(editorEmail)) {
            throw new BadRequestException("Owners cannot delete their own account");
        }
        refreshTokens.deleteByUser(user);
        users.delete(user);
    }
}
