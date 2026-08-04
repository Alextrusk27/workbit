package ru.workbit.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.auth.dto.*;
import ru.workbit.email.LoginCodeEmailEvent;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.security.service.JWTService;
import ru.workbit.auth.model.User;
import ru.workbit.auth.repository.UserJPARepository;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private final UserJPARepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final LoginCodeService loginCodeService;
    private final JWTService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        log.info("Logout success");
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new UserResponse(user.getEmail(), user.getCreated());
    }

    @Transactional
    public void requestCode(RequestCodeRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseGet(() -> createUser(request.email()));

        issueCode(user);
        log.info("Login code requested uid={}", user.getId());
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenResponse verifyCode(VerifyCodeRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid code"));
        loginCodeService.consume(user, request.code());

        user.setEmailVerified(true);
        TokenResponse tokens = issueTokens(user);
        log.info("Login success uid={}", user.getId());
        return tokens;
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        checkRefreshTokenNotNull(refreshToken);
        User user = refreshTokenService.consume(refreshToken);
        TokenResponse tokens = issueTokens(user);
        log.info("Token refreshed uid={}", user.getId());
        return tokens;
    }

    @Transactional
    public void deleteUser(UUID userId) {
        userRepository.deleteById(userId);
        log.info("User deleted uid={}", userId);
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .build());
    }

    private void issueCode(User user) {
        String rawCode = loginCodeService.issue(user);
        eventPublisher.publishEvent(new LoginCodeEmailEvent(user.getEmail(), rawCode));
    }

    private TokenResponse issueTokens(User user) {
        user.setLastSeen(Instant.now());
        user.setDeletionWarnedAt(null);
        String refreshToken = refreshTokenService.issue(user);
        return new TokenResponse(jwtService.generateToken(user), refreshToken);
    }

    private void checkRefreshTokenNotNull(String refreshToken) {
        if (refreshToken == null) {
            throw new BadCredentialsException("Refresh token missing");
        }
    }
}
