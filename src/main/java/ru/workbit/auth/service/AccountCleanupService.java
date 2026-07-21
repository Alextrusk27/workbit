package ru.workbit.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.auth.model.User;
import ru.workbit.auth.repository.UserJPARepository;
import ru.workbit.email.AccountDeletionWarningEmailEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountCleanupService {
    private static final Duration WARN_AFTER = Duration.ofDays(335);
    private static final Duration DELETE_AFTER_WARN = Duration.ofDays(30);

    private final UserJPARepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupInactiveAccounts() {
        Instant now = Instant.now();
        warnInactive(now.minus(WARN_AFTER));
        deleteExpired(now.minus(DELETE_AFTER_WARN));
    }

    private void warnInactive(Instant threshold) {
        List<User> inactive = userRepository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(threshold);
        Instant now = Instant.now();
        for (User user : inactive) {
            user.setDeletionWarnedAt(now);
            eventPublisher.publishEvent(new AccountDeletionWarningEmailEvent(user.getEmail()));
        }
        if (!inactive.isEmpty()) {
            log.info("Inactive account deletion warnings issued: {}", inactive.size());
        }
    }

    private void deleteExpired(Instant threshold) {
        int deleted = userRepository.deleteByDeletionWarnedAtBefore(threshold);
        if (deleted > 0) {
            log.info("Inactive accounts deleted: {}", deleted);
        }
    }
}
