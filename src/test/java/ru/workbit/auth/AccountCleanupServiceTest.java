package ru.workbit.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.workbit.auth.model.User;
import ru.workbit.auth.repository.UserJPARepository;
import ru.workbit.auth.service.AccountCleanupService;
import ru.workbit.email.AccountDeletionWarningEmailEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountCleanupServiceTest")
class AccountCleanupServiceTest {

    private static final Duration WARN_AFTER = Duration.ofDays(335);
    private static final Duration DELETE_AFTER_WARN = Duration.ofDays(30);

    @Mock
    UserJPARepository userRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    AccountCleanupService service;

    private User aUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("$argon2id$encoded")
                .emailVerified(true)
                .build();
    }

    @Nested
    @DisplayName("CleanupInactiveAccounts")
    class CleanupInactiveAccounts {

        @Test
        @DisplayName("Проставляет deletionWarnedAt и публикует по одному событию на каждого найденного пользователя")
        void warnsInactiveUsersAndPublishesEvents() {
            // given
            var user1 = aUser("user1@example.com");
            var user2 = aUser("user2@example.com");
            when(userRepository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(any()))
                    .thenReturn(List.of(user1, user2));
            when(userRepository.deleteByDeletionWarnedAtBefore(any())).thenReturn(5);

            // when
            service.cleanupInactiveAccounts();

            // then
            assertThat(user1.getDeletionWarnedAt()).isNotNull()
                    .isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
            assertThat(user2.getDeletionWarnedAt()).isNotNull()
                    .isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));

            var eventCaptor = ArgumentCaptor.forClass(AccountDeletionWarningEmailEvent.class);
            verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .extracting(AccountDeletionWarningEmailEvent::email)
                    .containsExactlyInAnyOrder("user1@example.com", "user2@example.com");

            verify(userRepository).deleteByDeletionWarnedAtBefore(any());
        }

        @Test
        @DisplayName("Передаёт порог ~335 дней в findByLastSeenBeforeAndDeletionWarnedAtIsNull")
        void passesWarnThresholdOf335Days() {
            // given
            when(userRepository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(any())).thenReturn(List.of());
            Instant before = Instant.now();

            // when
            service.cleanupInactiveAccounts();

            // then
            var thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(userRepository).findByLastSeenBeforeAndDeletionWarnedAtIsNull(thresholdCaptor.capture());
            Instant expected = before.minus(WARN_AFTER);
            assertThat(thresholdCaptor.getValue()).isCloseTo(expected, within(1, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("Передаёт порог ~30 дней в deleteByDeletionWarnedAtBefore")
        void passesDeleteThresholdOf30Days() {
            // given
            when(userRepository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(any())).thenReturn(List.of());
            Instant before = Instant.now();

            // when
            service.cleanupInactiveAccounts();

            // then
            var thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(userRepository).deleteByDeletionWarnedAtBefore(thresholdCaptor.capture());
            Instant expected = before.minus(DELETE_AFTER_WARN);
            assertThat(thresholdCaptor.getValue()).isCloseTo(expected, within(1, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("Ничего не делает, когда неактивных пользователей нет, но всё равно вызывает удаление истёкших")
        void doesNothingWhenNoInactiveUsersFound() {
            // given
            when(userRepository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(any())).thenReturn(List.of());

            // when
            service.cleanupInactiveAccounts();

            // then
            verifyNoInteractions(eventPublisher);
            verify(userRepository).deleteByDeletionWarnedAtBefore(any());
        }
    }
}
