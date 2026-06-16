package ru.workbit.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.auth.dto.*;
import ru.workbit.auth.model.VerificationToken;
import ru.workbit.email.ResetPasswordEmailEvent;
import ru.workbit.email.VerificationEmailEvent;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.security.service.JWTService;
import ru.workbit.user.model.User;
import ru.workbit.user.repository.UserJPARepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserJPARepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenService verificationTokenService;
    private final JWTService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = authenticate(request);
        return issueTokens(user);
    }

    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Transactional
    public void register(RegistrationRequest request) {
        User user = userRepository.findByEmail(request.email())
                .map(existing -> reactivate(existing, request.password()))
                .orElseGet(() -> createUser(request));

        String verifyToken = verificationTokenService.issue(user, VerificationToken.Type.EMAIL_VERIFICATION);
        eventPublisher.publishEvent(new VerificationEmailEvent(user.getEmail(), verifyToken));
    }

    @Transactional
    public TokenResponse verifyEmail(String token) {
        User user = verificationTokenService.consume(token, VerificationToken.Type.EMAIL_VERIFICATION);
        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid token");
        }
        user.setEmailVerified(true);
        return issueTokens(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        verifyPassword(request.oldPassword(), user.getPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAll(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        User user = refreshTokenService.consume(request.refreshToken());
        return issueTokens(user);
    }

    @Transactional
    public void remindPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email())
                .filter(User::isActive)
                .ifPresent(user -> {
                    String rawToken = verificationTokenService.issue(user, VerificationToken.Type.PASSWORD_RESET);
                    eventPublisher.publishEvent(new ResetPasswordEmailEvent(user.getEmail(), rawToken));
                });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = verificationTokenService.consume(token, VerificationToken.Type.PASSWORD_RESET);
        user.setPassword(passwordEncoder.encode(newPassword));
        refreshTokenService.revokeAll(user);
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        userRepository.findByEmail(request.email())
                .filter(User::isActive)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    String rawToken = verificationTokenService.issue(user, VerificationToken.Type.EMAIL_VERIFICATION);
                    eventPublisher.publishEvent(new VerificationEmailEvent(user.getEmail(), rawToken));
                });
    }

    @Transactional
    public void deactivateUser(UUID userId) {
        userRepository.findById(userId)
                .filter(User::isActive)
                .ifPresent(user -> {
                    user.setActive(false);
                    user.setDeactivated(Instant.now());
                    refreshTokenService.revokeAll(user);
                });
    }

    private TokenResponse issueTokens(User user) {
        String refreshToken = refreshTokenService.issue(user);
        return new TokenResponse(jwtService.generateToken(user), refreshToken);
    }

    private User createUser(RegistrationRequest request) {
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();
        return userRepository.save(user);
    }

    private User reactivate(User user, String rawPassword) {
        if (user.isActive()) {
            throw new BadCredentialsException("Email already in use");
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmailVerified(false);
        user.setActive(true);
        user.setDeactivated(null);
        refreshTokenService.revokeAll(user);
        return user;
    }

    private User authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!user.isEmailVerified()) {
            throw new BadCredentialsException("Email not verified");
        }
        verifyPassword(request.password(), user.getPassword());
        return user;
    }

    private void verifyPassword(String raw, String encoded) {
        if (!passwordEncoder.matches(raw, encoded)) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }
}
