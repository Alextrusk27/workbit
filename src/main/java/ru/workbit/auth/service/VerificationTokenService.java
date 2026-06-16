package ru.workbit.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.auth.model.VerificationToken;
import ru.workbit.auth.repository.VerificationTokenJPARepository;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.user.model.User;

import java.time.Instant;

/**
 * Жизненный цикл verification-токенов (подтверждение email и сброс пароля):
 * выпуск и одноразовое «погашение» с проверкой типа, срока и факта использования.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationTokenService {
    private final VerificationTokenJPARepository verificationRepository;
    private final TokenHasher tokenHasher;

    /** Выпускает токен заданного типа для пользователя, возвращает сырое значение для письма. */
    @Transactional
    public String issue(User user, VerificationToken.Type type) {
        verificationRepository.findAllByUserAndTypeAndUsedAtIsNull(user, type)
                .forEach(t -> t.setUsedAt(Instant.now()));

        String rawToken = tokenHasher.generate();
        verificationRepository.save(VerificationToken.builder()
                .user(user)
                .tokenHash(tokenHasher.hash(rawToken))
                .type(type)
                .build());
        return rawToken;
    }

    /**
     * Валидирует токен ожидаемого типа, помечает использованным и возвращает владельца.
     */
    @Transactional
    public User consume(String rawToken, VerificationToken.Type expectedType) {
        VerificationToken token = verificationRepository.findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid token"));

        validate(token, expectedType);

        token.setUsedAt(Instant.now());
        return token.getUser();
    }

    private void validate(VerificationToken token, VerificationToken.Type expectedType) {
        if (token.getType() != expectedType) {
            log.warn("Token ID '{}' has wrong type '{}', expected '{}'",
                    token.getId(), token.getType(), expectedType);
            throw new BadCredentialsException("Invalid token");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            log.info("Token has expired");
            throw new BadCredentialsException("Token has expired");
        }

        if (token.getUsedAt() != null) {
            log.info("Token has been used");
            throw new BadCredentialsException("Token has been used");
        }
    }
}
