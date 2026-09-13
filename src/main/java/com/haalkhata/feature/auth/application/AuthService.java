package com.haalkhata.feature.auth.application;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.security.JwtService;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.auth.domain.AppUser;
import com.haalkhata.feature.auth.domain.EmailVerification;
import com.haalkhata.feature.auth.domain.LoginSession;
import com.haalkhata.feature.auth.domain.OtpCode;
import com.haalkhata.feature.auth.domain.PendingRegistration;
import com.haalkhata.feature.auth.domain.RefreshToken;
import com.haalkhata.feature.auth.domain.Registration;
import com.haalkhata.feature.auth.infrastructure.AppUserRepository;
import com.haalkhata.feature.auth.infrastructure.EmailVerificationRepository;
import com.haalkhata.feature.auth.infrastructure.LoginSessionRepository;
import com.haalkhata.feature.auth.infrastructure.OtpCodeRepository;
import com.haalkhata.feature.auth.infrastructure.PendingRegistrationRepository;
import com.haalkhata.feature.auth.infrastructure.RefreshTokenRepository;
import com.haalkhata.feature.auth.infrastructure.RegistrationRepository;
import com.haalkhata.feature.user.domain.Role;
import com.haalkhata.feature.user.domain.UserRole;
import com.haalkhata.feature.user.infrastructure.RoleRepository;
import com.haalkhata.feature.user.infrastructure.UserRoleRepository;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final RegistrationRepository registrationRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppProperties props;

    public record RegisterRequest(String name, String email, String phone, String password) {
    }

    public record LoginRequest(String email, String password, String deviceId, String deviceName) {
    }

    public record GoogleRequest(String idToken, String deviceId, String deviceName) {
    }

    public record OtpRequest(String phone) {
    }

    public record VerifyOtpRequest(String phone, String code, String deviceId, String deviceName) {
    }

    public record VerifyRequest(String email, String code) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record UserDto(String userId, String email, String displayName, String phoneNumber,
                          String photoUrl, boolean emailVerified, boolean isNewUser,
                          String provider, List<String> roles) {
    }

    public record TokenResponse(String accessToken, String refreshToken, UserDto user) {
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("An account with this email already exists");
        }
        if (request.phone() != null && !request.phone().isBlank()
                && userRepository.existsByPhone(request.phone())) {
            throw BusinessException.conflict("An account with this phone number already exists");
        }
        LocalDateTime now = LocalDateTime.now();
        String userId = Ids.uuid();
        AppUser user = AppUser.builder()
                .id(userId)
                .name(request.name().trim())
                .email(email)
                .phone(request.phone() == null ? null : request.phone().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .emailVerified(false)
                .isNewUser(true)
                .provider("local")
                .shopId(Ids.shopId(userId))
                .role("OWNER")
                .createdAt(now)
                .updatedAt(now)
                .build();
        userRepository.save(user);

        roleRepository.findByCode("OWNER").ifPresent(role -> userRoleRepository.save(
                UserRole.builder().id(Ids.uuid()).userId(userId).roleId(role.getId()).build()));

        pendingRegistration(user, request.name().trim(), email, request.phone());
        issueVerificationCode(email);
        log.info("USER_REGISTERED userId={} email={} name={}", userId, email, request.name());
    }

    private void pendingRegistration(AppUser user, String name, String email, String phone) {
        LocalDateTime now = LocalDateTime.now();
        pendingRegistrationRepository.save(PendingRegistration.builder()
                .id(Ids.uuid())
                .userId(user.getId())
                .shopName(null)
                .ownerName(name)
                .email(email)
                .phoneNumber(phone)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Transactional
    public void sendVerificationCode(String email) {
        issueVerificationCode(email.trim().toLowerCase());
        log.info("VERIFICATION_CODE_SENT email={}", email.trim().toLowerCase());
    }

    private void issueVerificationCode(String email) {
        String code = Ids.randomCode(6);
        emailVerificationRepository.save(EmailVerification.builder()
                .id(Ids.uuid())
                .email(email)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(props.getVerificationCodeTtlMinutes()))
                .verified(false)
                .createdAt(LocalDateTime.now())
                .build());
        mailService.sendVerificationCode(email, code);
    }

    @Transactional
    public void verifyEmail(VerifyRequest request) {
        String email = request.email().trim().toLowerCase();
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> BusinessException.badRequest("No verification code was sent to this email"));
        if (verification.isVerified() || verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.badRequest("This verification code has expired. Please request a new one.");
        }
        if (!verification.getCode().equals(request.code().trim())) {
            throw BusinessException.badRequest("The verification code is incorrect");
        }
        verification.setVerified(true);
        emailVerificationRepository.save(verification);

        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        user.setEmailVerified(true);
        user.setNewUser(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        Registration registration = Registration.builder()
                .id(Ids.uuid())
                .userId(user.getId())
                .shopId(user.getShopId())
                .email(user.getEmail())
                .name(user.getName())
                .phoneNumber(user.getPhone())
                .photoUrl(user.getPhotoUrl())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        registrationRepository.save(registration);

        // Trial is granted lazily by SubscriptionService.status() on the
        // shop's first contact — the auth service stays business-free.

        log.info("EMAIL_VERIFIED userId={} email={}", user.getId(), email);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS",
                        "Invalid email or password", HttpStatus.UNAUTHORIZED));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("LOGIN_FAILED email={} reason=bad-password", request.email());
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
        }
        if (!user.isEmailVerified()) {
            log.warn("LOGIN_BLOCKED email={} reason=email-not-verified", request.email());
            throw new BusinessException("VERIFICATION_REQUIRED",
                    "Please verify your email before signing in", HttpStatus.FORBIDDEN);
        }
        TokenResponse response = issueTokens(user, request.deviceId(), request.deviceName());
        log.info("LOGIN_SUCCESS userId={} email={} device={}", user.getId(), user.getEmail(), request.deviceName());
        return response;
    }

    @Transactional
    public TokenResponse loginWithGoogle(GoogleRequest request) {
        Claims claims = googleTokenVerifier.verify(request.idToken());
        String email = claims.get("email", String.class);
        String name = claims.get("name", String.class);
        String picture = claims.get("picture", String.class);
        if (email == null) {
            throw BusinessException.badRequest("Google account has no email");
        }
        AppUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean newUser = false;
        if (user == null) {
            user = AppUser.builder()
                    .id(Ids.uuid())
                    .name(name == null ? email : name)
                    .email(email.toLowerCase())
                    .photoUrl(picture)
                    .emailVerified(true)
                    .isNewUser(true)
                    .provider("google")
                    .shopId(Ids.shopId(Ids.uuid()))
                    .role("OWNER")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            String newUserId = user.getId();
            roleRepository.findByCode("OWNER").ifPresent(role -> userRoleRepository.save(
                    UserRole.builder().id(Ids.uuid()).userId(newUserId).roleId(role.getId()).build()));
            newUser = true;
            user.setShopId(Ids.shopId(user.getId()));
            userRepository.save(user);
            log.info("USER_REGISTERED_VIA_GOOGLE userId={} email={}", user.getId(), email);
        }
        user.setNewUser(newUser);
        userRepository.save(user);
        TokenResponse response = issueTokens(user, request.deviceId(), request.deviceName());
        log.info("GOOGLE_LOGIN_SUCCESS userId={} email={}", user.getId(), email);
        return response;
    }

    @Transactional
    public void sendOtp(OtpRequest request) {
        String phone = request.phone().trim();
        String code = Ids.randomCode(6);
        otpCodeRepository.save(OtpCode.builder()
                .id(Ids.uuid())
                .phone(phone)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(props.getOtpTtlMinutes()))
                .verified(false)
                .createdAt(LocalDateTime.now())
                .build());
        mailService.sendOtp(phone, code);
        log.info("OTP_SENT phone={}", phone);
    }

    @Transactional
    public TokenResponse verifyOtp(VerifyOtpRequest request) {
        String phone = request.phone().trim();
        OtpCode otp = otpCodeRepository.findTopByPhoneOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> BusinessException.badRequest("No OTP was sent to this phone"));
        if (otp.isVerified() || otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.badRequest("This OTP has expired. Please request a new one.");
        }
        if (!otp.getCode().equals(request.code().trim())) {
            log.warn("OTP_VERIFY_FAILED phone={} reason=wrong-code", phone);
            throw BusinessException.badRequest("The OTP is incorrect");
        }
        otp.setVerified(true);
        otpCodeRepository.save(otp);

        AppUser user = userRepository.findByPhone(phone).orElseGet(() -> {
            AppUser fresh = AppUser.builder()
                    .id(Ids.uuid())
                    .name(phone)
                    .phone(phone)
                    .emailVerified(true)
                    .isNewUser(true)
                    .provider("phone")
                    .shopId(Ids.shopId(Ids.uuid()))
                    .role("OWNER")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(fresh);
            fresh.setShopId(Ids.shopId(fresh.getId()));
            userRepository.save(fresh);
            log.info("USER_REGISTERED_VIA_OTP userId={} phone={}", fresh.getId(), phone);
            return fresh;
        });
        TokenResponse response = issueTokens(user, request.deviceId(), request.deviceName());
        log.info("OTP_LOGIN_SUCCESS userId={} phone={}", user.getId(), phone);
        return response;
    }

    @Transactional
    public void forgotPassword(String email) {
        String normalized = email.trim().toLowerCase();
        Optional<AppUser> user = userRepository.findByEmailIgnoreCase(normalized);
        if (user.isEmpty()) {
            log.info("FORGOT_PASSWORD no-account email={}", normalized);
            return;
        }
        String code = Ids.randomCode(6);
        emailVerificationRepository.save(EmailVerification.builder()
                .id(Ids.uuid())
                .email(normalized)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(props.getVerificationCodeTtlMinutes()))
                .verified(false)
                .createdAt(LocalDateTime.now())
                .build());
        mailService.sendPasswordResetCode(normalized, code);
        log.info("PASSWORD_RESET_CODE_SENT email={}", normalized);
    }

    @Transactional
    public void changePassword(CurrentUser currentUser, ChangePasswordRequest request) {
        AppUser user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CURRENT_PASSWORD", "Current password is incorrect", HttpStatus.UNAUTHORIZED);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        refreshTokenRepository.deleteByUserId(user.getId());
        log.info("PASSWORD_CHANGED userId={}", user.getId());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> BusinessException.unauthorized("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw BusinessException.unauthorized("Refresh token has expired");
        }
        AppUser user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> BusinessException.unauthorized("User no longer exists"));
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        TokenResponse response = issueTokens(user, null, null);
        log.info("TOKEN_REFRESHED userId={}", user.getId());
        return response;
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("LOGOUT userId={}", token.getUserId());
        });
    }

    private TokenResponse issueTokens(AppUser user, String deviceId, String deviceName) {
        String refreshToken = jwtService.generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .id(Ids.uuid())
                .userId(user.getId())
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusDays(props.getRefreshTokenDays()))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build());

        if (deviceId != null && !deviceId.isBlank()) {
            Optional<LoginSession> existing = loginSessionRepository.findByUserIdAndDeviceId(user.getId(), deviceId);
            LoginSession session = existing.map(s -> {
                s.setLastLoginAt(LocalDateTime.now());
                s.setDeviceName(deviceName);
                return s;
            }).orElseGet(() -> LoginSession.builder()
                    .id(Ids.uuid())
                    .userId(user.getId())
                    .deviceId(deviceId)
                    .deviceName(deviceName)
                    .lastLoginAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build());
            loginSessionRepository.save(session);
        }

        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getId());
        if (roles.isEmpty()) {
            roles = List.of(user.getRole());
        }
        String access = jwtService.issueAccessToken(user.getId(), user.getEmail(),
                user.getName(), user.getShopId(), user.getRole(), roles);
        UserDto dto = new UserDto(user.getId(), user.getEmail(), user.getName(), user.getPhone(),
                user.getPhotoUrl(), user.isEmailVerified(), user.isNewUser(), user.getProvider(), roles);
        return new TokenResponse(access, refreshToken, dto);
    }
}
