package ru.workbit.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.auth.model.RefreshToken;
import ru.workbit.auth.repository.RefreshTokenJPARepository;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.user.model.User;

import java.time.Instant;

/**
 * Жизненный цикл refresh-токенов: выпуск, ротация (с детектом повторного
 * использования отозванного токена) и ревокация.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {
    private final RefreshTokenJPARepository refreshRepository;
    private final TokenHasher tokenHasher;

    /** Выпускает новый refresh-токен для пользователя, возвращает сырое значение для клиента. */
    @Transactional
    public String issue(User user) {
        String rawToken = tokenHasher.generate();
        refreshRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHasher.hash(rawToken))
                .build());
        return rawToken;
    }

    /**
     * Валидирует предъявленный токен и отзывает его (одноразовость).
     * Повторное предъявление уже отозванного токена — сигнал кражи: отзываем все сессии пользователя.
     * Возвращает владельца токена для выпуска новой пары.
     */
    @Transactional
    public User consume(String rawToken) {
        RefreshToken token = refreshRepository.findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        User user = token.getUser();

        if (token.isRevoked()) {
            revokeAll(user);
            log.warn("Revoked token reuse detected for user '{}'. All sessions have been revoked.", user.getId());
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        token.setRevoked(true);
        return user;
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshRepository.revokeToken(tokenHasher.hash(rawToken));
    }

    @Transactional
    public void revokeAll(User user) {
        refreshRepository.revokeAllByUser(user);
    }
}
