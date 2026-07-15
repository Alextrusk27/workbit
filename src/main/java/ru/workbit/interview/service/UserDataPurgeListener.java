package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.workbit.auth.UserReactivatedEvent;
import ru.workbit.interview.repository.TrainingSessionRepository;
import ru.workbit.interview.repository.VacancySessionRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDataPurgeListener {
    private final TrainingSessionRepository trainingSessionRepository;
    private final VacancySessionRepository vacancySessionRepository;

    @EventListener
    public void onUserReactivated(UserReactivatedEvent event) {
        trainingSessionRepository.deleteAllByUserId(event.userId());
        vacancySessionRepository.deleteAllByUserId(event.userId());
        log.info("Interview data purged on reactivation uid={}", event.userId());
    }
}
