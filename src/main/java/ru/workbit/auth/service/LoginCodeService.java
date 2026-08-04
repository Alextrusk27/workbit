package ru.workbit.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.auth.model.LoginCode;
import ru.workbit.auth.model.User;
import ru.workbit.auth.repository.LoginCodeJPARepository;
import ru.workbit.exception.BadCredentialsException;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Жизненный цикл одноразовых кодов входа: выпуск и одноразовое «погашение»
 * с проверкой срока, числа попыток и факта использования.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;

    private final LoginCodeJPARepository loginCodeRepository;
    private final TokenHasher tokenHasher;

    /**
     * Выпускает код для пользователя, гасит предыдущие активные и возвращает сырое значение для письма.
     */
    @Transactional
    public String issue(User user) {
        loginCodeRepository.findAllByUserAndUsedAtIsNull(user)
                .forEach(code -> code.setUsedAt(Instant.now()));

        String rawCode = "%06d".formatted(RANDOM.nextInt(1_000_000));
        loginCodeRepository.save(LoginCode.builder()
                .user(user)
                .codeHash(hash(user, rawCode))
                .build());
        return rawCode;
    }

    /**
     * Валидирует код пользователя и помечает использованным.
     * Инкремент попыток при неверном коде должен пережить исключение — отсюда noRollbackFor.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public void consume(User user, String rawCode) {
        LoginCode code = loginCodeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user)
                .orElseThrow(() -> new BadCredentialsException("Invalid code"));

        if (code.getExpiresAt().isBefore(Instant.now())) {
            log.info("Login code has expired uid={}", user.getId());
            throw new BadCredentialsException("Code has expired");
        }

        if (code.getAttempts() >= MAX_ATTEMPTS) {
            log.info("Login code attempts exhausted uid={}", user.getId());
            throw new BadCredentialsException("Too many attempts");
        }

        if (!code.getCodeHash().equals(hash(user, rawCode))) {
            code.setAttempts(code.getAttempts() + 1);
            log.info("Invalid login code uid={} attempts={}", user.getId(), code.getAttempts());
            throw new BadCredentialsException("Invalid code");
        }

        code.setUsedAt(Instant.now());
    }

    private String hash(User user, String rawCode) {
        return tokenHasher.hash(user.getId() + ":" + rawCode);
    }
}
