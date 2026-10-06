package ru.workbit.resume.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.workbit.auth.UsersDeletedEvent;
import ru.workbit.resume.ResumeDeletedEvent;

@Component
@ConditionalOnBooleanProperty(name = "app.resume.enabled", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class ResumeFilesCleaner {
    private final ResumeFileStorage storage;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResumeDeleted(ResumeDeletedEvent event) {
        try {
            storage.delete(event.userId(), event.resumeId());
        } catch (RuntimeException e) {
            log.error("Resume file delete failed uid={} resumeId={}", event.userId(), event.resumeId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUsersDeleted(UsersDeletedEvent event) {
        for (UUID userId : event.userIds()) {
            try {
                storage.deleteUser(userId);
            } catch (RuntimeException e) {
                log.error("Resume files delete failed uid={}", userId, e);
            }
        }
    }
}
