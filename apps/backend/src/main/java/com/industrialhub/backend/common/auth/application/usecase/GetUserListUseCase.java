package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.auth.application.dto.UserResponse;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetUserListUseCase {

    private final UserRepository userRepository;

    public GetUserListUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> execute() {
        return userRepository.findAllByOrderByUsernameAsc()
                .stream()
                .map(UserResponse::from)
                .toList();
    }
}
