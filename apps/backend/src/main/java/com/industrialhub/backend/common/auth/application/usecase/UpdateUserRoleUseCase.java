package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.auth.application.dto.UpdateUserRoleRequest;
import com.industrialhub.backend.common.auth.application.dto.UserResponse;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateUserRoleUseCase {

    private final UserRepository userRepository;

    public UpdateUserRoleUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse execute(UUID userId, UpdateUserRoleRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setRole(request.role());
        return UserResponse.from(userRepository.save(user));
    }
}
