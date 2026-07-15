package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.auth.UserReactivatedEvent;
import ru.workbit.interview.repository.TrainingSessionRepository;
import ru.workbit.interview.repository.VacancySessionRepository;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDataPurgeListenerTest")
class UserDataPurgeListenerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    TrainingSessionRepository trainingSessionRepository;
    @Mock
    VacancySessionRepository vacancySessionRepository;

    @InjectMocks
    UserDataPurgeListener listener;

    @Nested
    @DisplayName("OnUserReactivated")
    class OnUserReactivated {

        @Test
        @DisplayName("Удаляет тренировочные и вакансионные сессии пользователя из события")
        void purgesTrainingAndVacancySessionsForEventUser() {
            // given
            var event = new UserReactivatedEvent(USER_ID);

            // when
            listener.onUserReactivated(event);

            // then
            verify(trainingSessionRepository).deleteAllByUserId(USER_ID);
            verify(vacancySessionRepository).deleteAllByUserId(USER_ID);
        }
    }
}
