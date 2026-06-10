package com.industrialhub.backend.common.auth.presentation;

import com.industrialhub.backend.common.auth.application.dto.LoginRequestDto;
import com.industrialhub.backend.common.auth.application.dto.LoginResponseDto;
import com.industrialhub.backend.common.auth.application.usecase.LoginUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private LoginUseCase loginUseCase;

    @InjectMocks
    private AuthController authController;

    @Test
    void login_usesXForwardedForWhenPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");

        LoginRequestDto loginRequest = new LoginRequestDto("user@test.com", "pass");
        LoginResponseDto loginResponse = new LoginResponseDto("token", "user", "OPERATOR", "user@test.com", 28800000L, false);
        when(loginUseCase.execute(any(), eq("203.0.113.5"))).thenReturn(loginResponse);

        ResponseEntity<LoginResponseDto> response = authController.login(loginRequest, request);

        assertThat(response.getBody()).isEqualTo(loginResponse);
        verify(loginUseCase).execute(loginRequest, "203.0.113.5");
        verify(request, never()).getRemoteAddr();
    }

    @Test
    void login_fallsBackToRemoteAddrWhenXForwardedForAbsent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");

        LoginRequestDto loginRequest = new LoginRequestDto("user@test.com", "pass");
        LoginResponseDto loginResponse = new LoginResponseDto("token", "user", "OPERATOR", "user@test.com", 28800000L, false);
        when(loginUseCase.execute(any(), eq("192.168.1.10"))).thenReturn(loginResponse);

        ResponseEntity<LoginResponseDto> response = authController.login(loginRequest, request);

        assertThat(response.getBody()).isEqualTo(loginResponse);
        verify(loginUseCase).execute(loginRequest, "192.168.1.10");
        verify(request).getRemoteAddr();
    }
}
