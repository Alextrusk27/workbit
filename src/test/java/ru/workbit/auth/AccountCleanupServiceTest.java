package ru.workbit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
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
import ru.workbit.billing.service.LimitService;
import ru.workbit.email.AccountDeletionWarningEmailEvent;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountCleanupServiceTest")
class AccountCleanupServiceTest {

    private static final Duration WARN_AFTER = Duration.ofDays(335);
    private static final Duration DELETE_AFTER_WARN = Duration.ofDays(30);

    @Mock
    UserJPARepository userRepository;
    @Mock
    LimitService limitService;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    AccountCleanupService service;

    private User aUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
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
            verifyNoInteractions(eventPublisher, limitService);
            verify(userRepository).deleteByDeletionWarnedAtBefore(any());
        }

        @Test
        @DisplayName("Снимает гранты приветственных лимитов по адресам истёкших аккаунтов до их удаления")
        void revokesWelcomeGrantsBeforeDeletingExpiredUsers() {
            // given
            when(userRepository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(any())).thenReturn(List.of());
            when(userRepository.findByDeletionWarnedAtBefore(any()))
                    .thenReturn(List.of(aUser("expired1@example.com"), aUser("expired2@example.com")));

            // when
            service.cleanupInactiveAccounts();

            // then
            var inOrderCheck = inOrder(limitService, userRepository);
            inOrderCheck.verify(limitService)
                    .revokeWelcome(List.of("expired1@example.com", "expired2@example.com"));
            inOrderCheck.verify(userRepository).deleteByDeletionWarnedAtBefore(any());
        }

        @Test
        @DisplayName("Публикует UsersDeletedEvent с id всех истёкших пользователей в порядке выборки")
        void publishesUsersDeletedEventWithExpiredIds() {
            // given
            var expired1 = aUser("expired1@example.com");
            var expired2 = aUser("expired2@example.com");
            var expired3 = aUser("expired3@example.com");
            when(userRepository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(any())).thenReturn(List.of());
            when(userRepository.findByDeletionWarnedAtBefore(any()))
                    .thenReturn(List.of(expired1, expired2, expired3));

            // when
            service.cleanupInactiveAccounts();

            // then
            var eventCaptor = ArgumentCaptor.forClass(UsersDeletedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().userIds())
                    .containsExactly(expired1.getId(), expired2.getId(), expired3.getId());
        }

        @Test
        @DisplayName("Публикует UsersDeletedEvent после снятия приветственных грантов и до bulk-удаления")
        void publishesUsersDeletedEventBeforeBulkDelete() {
            // given
            var expired = aUser("expired@example.com");
            when(userRepository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(any())).thenReturn(List.of());
            when(userRepository.findByDeletionWarnedAtBefore(any())).thenReturn(List.of(expired));

            // when
            service.cleanupInactiveAccounts();

            // then
            var inOrderCheck = inOrder(limitService, eventPublisher, userRepository);
            inOrderCheck.verify(limitService).revokeWelcome(List.of("expired@example.com"));
            inOrderCheck.verify(eventPublisher).publishEvent(new UsersDeletedEvent(List.of(expired.getId())));
            inOrderCheck.verify(userRepository).deleteByDeletionWarnedAtBefore(any());
        }

        @Test
        @DisplayName("Не публикует UsersDeletedEvent, когда истёкших пользователей нет")
        void doesNotPublishUsersDeletedEventWhenNoExpired() {
            // given
            var inactive = aUser("inactive@example.com");
            when(userRepository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(any()))
                    .thenReturn(List.of(inactive));
            when(userRepository.findByDeletionWarnedAtBefore(any())).thenReturn(List.of());

            // when
            service.cleanupInactiveAccounts();

            // then
            var eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .hasOnlyElementsOfType(AccountDeletionWarningEmailEvent.class)
                    .noneMatch(UsersDeletedEvent.class::isInstance);
        }
    }
}
