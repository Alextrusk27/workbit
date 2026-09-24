package ru.workbit.auth.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.auth.repository.LoginCodeJPARepository;
import ru.workbit.auth.repository.RefreshTokenJPARepository;

/**
 * Ежесуточно удаляет истёкшие refresh-токены и коды входа.
 * Код удаляется с запасом в сутки, чтобы опоздавший ввод получал «Code has expired», а не «Invalid code».
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthTokenCleanupService {
    private static final Duration LOGIN_CODE_RETENTION = Duration.ofDays(1);

    private final RefreshTokenJPARepository refreshTokenRepository;
    private final LoginCodeJPARepository loginCodeRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void deleteExpired() {
        Instant now = Instant.now();
        int refreshTokens = refreshTokenRepository.deleteByExpiresAtBefore(now);
        int loginCodes = loginCodeRepository.deleteByExpiresAtBefore(now.minus(LOGIN_CODE_RETENTION));
        if (refreshTokens > 0 || loginCodes > 0) {
            log.info("Expired auth tokens deleted: refreshTokens={} loginCodes={}", refreshTokens, loginCodes);
        }
    }
}
