package ru.workbit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.auth.repository.LoginCodeJPARepository;
import ru.workbit.auth.repository.RefreshTokenJPARepository;
import ru.workbit.auth.service.AuthTokenCleanupService;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthTokenCleanupServiceTest")
class AuthTokenCleanupServiceTest {

    private static final Duration LOGIN_CODE_RETENTION = Duration.ofDays(1);

    @Mock
    RefreshTokenJPARepository refreshTokenRepository;
    @Mock
    LoginCodeJPARepository loginCodeRepository;

    @InjectMocks
    AuthTokenCleanupService service;

    @Nested
    @DisplayName("DeleteExpired")
    class DeleteExpired {

        @Test
        @DisplayName("Удаляет refresh-токены с порогом ~сейчас")
        void deletesRefreshTokensWithThresholdCloseToNow() {
            // given
            when(refreshTokenRepository.deleteByExpiresAtBefore(any())).thenReturn(0);
            when(loginCodeRepository.deleteByExpiresAtBefore(any())).thenReturn(0);
            Instant before = Instant.now();

            // when
            service.deleteExpired();

            // then
            var thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(refreshTokenRepository).deleteByExpiresAtBefore(thresholdCaptor.capture());
            assertThat(thresholdCaptor.getValue()).isCloseTo(before, within(1, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("Удаляет коды входа с порогом ~сейчас минус сутки")
        void deletesLoginCodesWithThresholdCloseToNowMinusOneDay() {
            // given
            when(refreshTokenRepository.deleteByExpiresAtBefore(any())).thenReturn(0);
            when(loginCodeRepository.deleteByExpiresAtBefore(any())).thenReturn(0);
            Instant before = Instant.now();

            // when
            service.deleteExpired();

            // then
            var thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(loginCodeRepository).deleteByExpiresAtBefore(thresholdCaptor.capture());
            assertThat(thresholdCaptor.getValue())
                    .isCloseTo(before.minus(LOGIN_CODE_RETENTION), within(1, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("Пороги согласованы: разница между порогом кодов и порогом токенов ровно 1 день")
        void thresholdsAreConsistentByExactlyOneDay() {
            // given
            when(refreshTokenRepository.deleteByExpiresAtBefore(any())).thenReturn(1);
            when(loginCodeRepository.deleteByExpiresAtBefore(any())).thenReturn(1);

            // when
            service.deleteExpired();

            // then
            var refreshThresholdCaptor = ArgumentCaptor.forClass(Instant.class);
            var loginCodeThresholdCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(refreshTokenRepository).deleteByExpiresAtBefore(refreshThresholdCaptor.capture());
            verify(loginCodeRepository).deleteByExpiresAtBefore(loginCodeThresholdCaptor.capture());

            Duration diff = Duration.between(loginCodeThresholdCaptor.getValue(), refreshThresholdCaptor.getValue());
            assertThat(diff).isEqualTo(LOGIN_CODE_RETENTION);
        }
    }
}
