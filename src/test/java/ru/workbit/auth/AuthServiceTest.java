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
import ru.workbit.auth.dto.*;
import ru.workbit.auth.model.User;
import ru.workbit.auth.repository.UserJPARepository;
import ru.workbit.auth.service.AuthService;
import ru.workbit.auth.service.LoginCodeService;
import ru.workbit.auth.service.RefreshTokenService;
import ru.workbit.email.LoginCodeEmailEvent;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.security.service.JWTService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceTest")
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String RAW_CODE = "123456";
    private static final String ACCESS_TOKEN = "jwt-access-token";
    private static final String REFRESH_TOKEN = "raw-refresh-token";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    UserJPARepository userRepository;
    @Mock
    RefreshTokenService refreshTokenService;
    @Mock
    LoginCodeService loginCodeService;
    @Mock
    JWTService jwtService;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    AuthService authService;

    private User verifiedUser() {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .emailVerified(true)
                .build();
    }

    private User unverifiedUser() {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .emailVerified(false)
                .build();
    }

    // -------------------------------------------------------------------------
    // logout
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Logout")
    class Logout {

        @Test
        @DisplayName("Отзывает refresh-токен")
        void revokesRefreshToken() {
            // when
            authService.logout(REFRESH_TOKEN);

            // then
            verify(refreshTokenService).revoke(REFRESH_TOKEN);
        }
    }

    // -------------------------------------------------------------------------
    // getProfile
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetProfile")
    class GetProfile {

        @Test
        @DisplayName("Возвращает email и дату регистрации для существующего пользователя")
        void returnsEmailAndCreatedForExistingUser() {
            // given
            var user = verifiedUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            // when
            var result = authService.getProfile(USER_ID);

            // then
            assertThat(result.email()).isEqualTo(EMAIL);
            assertThat(result.created()).isEqualTo(user.getCreated());
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда пользователь не найден")
        void throwsWhenUserNotFound() {
            // given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> authService.getProfile(USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
        }
    }

    // -------------------------------------------------------------------------
    // requestCode
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("RequestCode")
    class RequestCode {

        @Test
        @DisplayName("Выпускает код существующему пользователю, не создавая нового")
        void issuesCodeForExistingUserWithoutCreatingNew() {
            // given
            var user = verifiedUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(loginCodeService.issue(user)).thenReturn(RAW_CODE);

            // when
            authService.requestCode(new RequestCodeRequest(EMAIL));

            // then
            verify(userRepository, never()).save(any());

            var eventCaptor = ArgumentCaptor.forClass(LoginCodeEmailEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().email()).isEqualTo(EMAIL);
            assertThat(eventCaptor.getValue().code()).isEqualTo(RAW_CODE);
        }

        @Test
        @DisplayName("Создаёт пользователя и выпускает код, когда email неизвестен")
        void createsUserAndIssuesCodeForUnknownEmail() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            var savedUser = unverifiedUser();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(loginCodeService.issue(savedUser)).thenReturn(RAW_CODE);

            // when
            authService.requestCode(new RequestCodeRequest(EMAIL));

            // then
            var userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getEmail()).isEqualTo(EMAIL);

            var eventCaptor = ArgumentCaptor.forClass(LoginCodeEmailEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().email()).isEqualTo(EMAIL);
            assertThat(eventCaptor.getValue().code()).isEqualTo(RAW_CODE);
        }
    }

    // -------------------------------------------------------------------------
    // verifyCode
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("VerifyCode")
    class VerifyCode {

        @Test
        @DisplayName("Подтверждает email и возвращает токены при верном коде")
        void verifiesEmailAndReturnsTokens() {
            // given
            var user = unverifiedUser();
            user.setLastSeen(Instant.now().minus(Duration.ofDays(400)));
            user.setDeletionWarnedAt(Instant.now().minus(Duration.ofDays(10)));
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(jwtService.generateToken(any())).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.issue(any())).thenReturn(REFRESH_TOKEN);

            // when
            var result = authService.verifyCode(new VerifyCodeRequest(EMAIL, RAW_CODE));

            // then
            verify(loginCodeService).consume(user, RAW_CODE);
            assertThat(user.isEmailVerified()).isTrue();
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(user.getLastSeen()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
            assertThat(user.getDeletionWarnedAt()).isNull();
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда пользователь не найден")
        void throwsWhenUserNotFound() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> authService.verifyCode(new VerifyCodeRequest(EMAIL, RAW_CODE)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid code");

            verifyNoInteractions(loginCodeService, jwtService, refreshTokenService);
        }

        @Test
        @DisplayName("Прокидывает BadCredentialsException от LoginCodeService, не подтверждая email")
        void propagatesExceptionFromLoginCodeService() {
            // given
            var user = unverifiedUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            doThrow(new BadCredentialsException("Invalid code"))
                    .when(loginCodeService).consume(user, RAW_CODE);

            // when / then
            assertThatThrownBy(() -> authService.verifyCode(new VerifyCodeRequest(EMAIL, RAW_CODE)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid code");

            assertThat(user.isEmailVerified()).isFalse();
            verifyNoInteractions(jwtService, refreshTokenService);
        }
    }

    // -------------------------------------------------------------------------
    // refresh
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Refresh")
    class Refresh {

        @Test
        @DisplayName("Возвращает новую пару токенов по валидному refresh-токену")
        void returnsNewTokensForValidRefreshToken() {
            // given
            var user = verifiedUser();
            user.setLastSeen(Instant.now().minus(Duration.ofDays(400)));
            user.setDeletionWarnedAt(Instant.now().minus(Duration.ofDays(10)));
            when(refreshTokenService.consume(REFRESH_TOKEN)).thenReturn(user);
            when(jwtService.generateToken(user)).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.issue(user)).thenReturn("new-refresh-token");

            // when
            var result = authService.refresh(REFRESH_TOKEN);

            // then
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
            assertThat(user.getLastSeen()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
            assertThat(user.getDeletionWarnedAt()).isNull();
        }

        @Test
        @DisplayName("Прокидывает BadCredentialsException от RefreshTokenService")
        void propagatesExceptionFromRefreshTokenService() {
            // given
            when(refreshTokenService.consume(REFRESH_TOKEN))
                    .thenThrow(new BadCredentialsException("Invalid refresh token"));

            // when / then
            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid refresh token");

            verifyNoInteractions(jwtService);
            verify(refreshTokenService, never()).issue(any());
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда refresh-токен null")
        void throwsWhenRefreshTokenNull() {
            // when / then
            assertThatThrownBy(() -> authService.refresh(null))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Refresh token missing");

            verifyNoInteractions(refreshTokenService, jwtService);
        }
    }

    // -------------------------------------------------------------------------
    // deleteUser
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DeleteUser")
    class DeleteUser {

        @Test
        @DisplayName("Удаляет пользователя по id")
        void deletesUser() {
            // when
            authService.deleteUser(USER_ID);

            // then
            verify(userRepository).deleteById(USER_ID);
        }
    }
}
