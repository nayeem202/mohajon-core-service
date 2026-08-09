package com.haalkhata.feature.auth.api;

import com.haalkhata.feature.auth.application.AuthService;
import com.haalkhata.feature.auth.application.AuthService.ChangePasswordRequest;
import com.haalkhata.feature.auth.application.AuthService.GoogleRequest;
import com.haalkhata.feature.auth.application.AuthService.LoginRequest;
import com.haalkhata.feature.auth.application.AuthService.OtpRequest;
import com.haalkhata.feature.auth.application.AuthService.RegisterRequest;
import com.haalkhata.feature.auth.application.AuthService.TokenResponse;
import com.haalkhata.feature.auth.application.AuthService.VerifyOtpRequest;
import com.haalkhata.feature.auth.application.AuthService.VerifyRequest;
import com.haalkhata.core.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.OK)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.OK)
    public void resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.sendVerificationCode(request.email());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.OK)
    public void verifyEmail(@Valid @RequestBody VerifyRequest request) {
        authService.verifyEmail(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public TokenResponse google(@Valid @RequestBody GoogleRequest request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping("/send-otp")
    @ResponseStatus(HttpStatus.OK)
    public void sendOtp(@Valid @RequestBody OtpRequest request) {
        authService.sendOtp(request);
    }

    @PostMapping("/verify-otp")
    public TokenResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return authService.verifyOtp(request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    public void forgotPassword(@Valid @RequestBody EmailRequest request) {
        authService.forgotPassword(request.email());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.OK)
    public void changePassword(@AuthenticationPrincipal CurrentUser currentUser,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser, request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    public record EmailRequest(@NotBlank @Email String email) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }
}
