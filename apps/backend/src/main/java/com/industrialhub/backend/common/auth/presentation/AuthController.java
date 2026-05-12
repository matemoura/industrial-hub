package com.industrialhub.backend.common.auth.presentation;

import com.industrialhub.backend.common.auth.application.dto.LoginRequestDto;
import com.industrialhub.backend.common.auth.application.dto.LoginResponseDto;
import com.industrialhub.backend.common.auth.application.usecase.LoginUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;

    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(loginUseCase.execute(request));
    }
}
