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
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.workbit.auth.dto.*;
import ru.workbit.auth.model.VerificationToken;
import ru.workbit.auth.service.AuthService;
import ru.workbit.auth.service.RefreshTokenService;
import ru.workbit.auth.service.VerificationTokenService;
import ru.workbit.email.ResetPasswordEmailEvent;
import ru.workbit.email.VerificationEmailEvent;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.security.service.JWTService;
import ru.workbit.auth.model.User;
import ru.workbit.auth.repository.UserJPARepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceTest")
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "P@ssw0rd123";
    private static final String ENCODED_PASSWORD = "$argon2id$encoded";
    private static final String ACCESS_TOKEN = "jwt-access-token";
    private static final String REFRESH_TOKEN = "raw-refresh-token";
    private static final String VERIFY_TOKEN = "raw-verify-token";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    UserJPARepository userRepository;
    @Mock
    RefreshTokenService refreshTokenService;
    @Mock
    VerificationTokenService verificationTokenService;
    @Mock
    JWTService jwtService;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    AuthService authService;

    private User activeVerifiedUser() {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .active(true)
                .emailVerified(true)
                .build();
    }

    private User activeUnverifiedUser() {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .active(true)
                .emailVerified(false)
                .build();
    }

    private User inactiveUser() {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .active(false)
                .emailVerified(false)
                .deactivated(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // login
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("Возвращает токены для активного верифицированного пользователя")
        void returnsTokensForActiveVerifiedUser() {
            // given
            var user = activeVerifiedUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtService.generateToken(any())).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.issue(any())).thenReturn(REFRESH_TOKEN);

            // when
            var result = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD));

            // then
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда пользователь не найден")
        void throwsWhenUserNotFound() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            verifyNoInteractions(passwordEncoder, refreshTokenService, jwtService, eventPublisher);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда пользователь неактивен")
        void throwsWhenUserInactive() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(inactiveUser()));

            // when / then
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            verifyNoInteractions(passwordEncoder, refreshTokenService, jwtService);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда email не подтверждён")
        void throwsWhenEmailNotVerified() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUnverifiedUser()));

            // when / then
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Email not verified");

            verifyNoInteractions(passwordEncoder, refreshTokenService, jwtService);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда пароль неверный")
        void throwsWhenWrongPassword() {
            // given
            var user = activeVerifiedUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

            // when / then
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            verifyNoInteractions(refreshTokenService, jwtService);
        }
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
            authService.logout(new LogoutRequest(REFRESH_TOKEN));

            // then
            verify(refreshTokenService).revoke(REFRESH_TOKEN);
        }
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        @DisplayName("Создаёт нового пользователя и публикует событие верификации")
        void createsNewUserAndPublishesEvent() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            var savedUser = activeUnverifiedUser();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(passwordEncoder.encode(any())).thenReturn(ENCODED_PASSWORD);
            when(verificationTokenService.issue(any(), eq(VerificationToken.Type.EMAIL_VERIFICATION)))
                    .thenReturn(VERIFY_TOKEN);

            // when
            authService.register(new RegistrationRequest(EMAIL, RAW_PASSWORD));

            // then
            var userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getEmail()).isEqualTo(EMAIL);

            var eventCaptor = ArgumentCaptor.forClass(VerificationEmailEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().email()).isEqualTo(EMAIL);
            assertThat(eventCaptor.getValue().token()).isEqualTo(VERIFY_TOKEN);
        }

        @Test
        @DisplayName("Реактивирует деактивированного пользователя и публикует событие верификации")
        void reactivatesDeactivatedUserAndPublishesEvent() {
            // given
            var existingUser = inactiveUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.encode(any())).thenReturn(ENCODED_PASSWORD);
            when(verificationTokenService.issue(any(), eq(VerificationToken.Type.EMAIL_VERIFICATION)))
                    .thenReturn(VERIFY_TOKEN);

            // when
            authService.register(new RegistrationRequest(EMAIL, RAW_PASSWORD));

            // then
            assertThat(existingUser.isActive()).isTrue();
            assertThat(existingUser.getDeactivated()).isNull();
            assertThat(existingUser.isEmailVerified()).isFalse();

            verify(refreshTokenService).revokeAll(existingUser);
            verify(eventPublisher).publishEvent(any(VerificationEmailEvent.class));
        }

        @Test
        @DisplayName("Бросает BadCredentialsException при попытке зарегистрироваться с уже занятым активным email")
        void throwsWhenActiveEmailAlreadyInUse() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeVerifiedUser()));

            // when / then
            assertThatThrownBy(() -> authService.register(new RegistrationRequest(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Email already in use");

            verifyNoInteractions(verificationTokenService, eventPublisher);
        }
    }

    // -------------------------------------------------------------------------
    // verifyEmail
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("VerifyEmail")
    class VerifyEmail {

        @Test
        @DisplayName("Подтверждает email и возвращает токены")
        void verifiesEmailAndReturnsTokens() {
            // given
            var user = activeUnverifiedUser();
            when(verificationTokenService.consume(VERIFY_TOKEN, VerificationToken.Type.EMAIL_VERIFICATION))
                    .thenReturn(user);
            when(jwtService.generateToken(any())).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.issue(any())).thenReturn(REFRESH_TOKEN);

            // when
            var result = authService.verifyEmail(VERIFY_TOKEN);

            // then
            assertThat(user.isEmailVerified()).isTrue();
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, если пользователь неактивен после consume")
        void throwsWhenUserInactiveAfterConsume() {
            // given
            var inactiveUser = inactiveUser();
            when(verificationTokenService.consume(VERIFY_TOKEN, VerificationToken.Type.EMAIL_VERIFICATION))
                    .thenReturn(inactiveUser);

            // when / then
            assertThatThrownBy(() -> authService.verifyEmail(VERIFY_TOKEN))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid token");

            verifyNoInteractions(jwtService);
            verify(refreshTokenService, never()).issue(any());
        }
    }

    // -------------------------------------------------------------------------
    // changePassword
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ChangePassword")
    class ChangePassword {

        private static final String NEW_PASSWORD = "N3wP@ssw0rd";
        private static final String NEW_ENCODED = "$argon2id$new-encoded";

        @Test
        @DisplayName("Меняет пароль и отзывает все сессии")
        void changesPasswordAndRevokesAllSessions() {
            // given
            var user = activeVerifiedUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_ENCODED);

            // when
            authService.changePassword(new ChangePasswordRequest(RAW_PASSWORD, NEW_PASSWORD), USER_ID);

            // then
            // dirty checking: проверяем состояние объекта, не verify(save)
            assertThat(user.getPassword()).isEqualTo(NEW_ENCODED);
            verify(refreshTokenService).revokeAll(user);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда пользователь не найден")
        void throwsWhenUserNotFound() {
            // given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> authService.changePassword(
                    new ChangePasswordRequest(RAW_PASSWORD, NEW_PASSWORD), USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");

            verifyNoInteractions(passwordEncoder, refreshTokenService);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException при неверном старом пароле")
        void throwsWhenOldPasswordWrong() {
            // given
            var user = activeVerifiedUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

            // when / then
            assertThatThrownBy(() -> authService.changePassword(
                    new ChangePasswordRequest(RAW_PASSWORD, NEW_PASSWORD), USER_ID))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            verify(refreshTokenService, never()).revokeAll(any());
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
            var user = activeVerifiedUser();
            when(refreshTokenService.consume(REFRESH_TOKEN)).thenReturn(user);
            when(jwtService.generateToken(user)).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.issue(user)).thenReturn("new-refresh-token");

            // when
            var result = authService.refresh(new RefreshRequest(REFRESH_TOKEN));

            // then
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        }

        @Test
        @DisplayName("Прокидывает BadCredentialsException от RefreshTokenService")
        void propagatesExceptionFromRefreshTokenService() {
            // given
            when(refreshTokenService.consume(REFRESH_TOKEN))
                    .thenThrow(new BadCredentialsException("Invalid refresh token"));

            // when / then
            assertThatThrownBy(() -> authService.refresh(new RefreshRequest(REFRESH_TOKEN)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid refresh token");

            verifyNoInteractions(jwtService);
            verify(refreshTokenService, never()).issue(any());
        }
    }

    // -------------------------------------------------------------------------
    // remindPassword
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("RemindPassword")
    class RemindPassword {

        @Test
        @DisplayName("Публикует событие сброса пароля для активного пользователя")
        void publishesResetEventForActiveUser() {
            // given
            var user = activeVerifiedUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(verificationTokenService.issue(user, VerificationToken.Type.PASSWORD_RESET))
                    .thenReturn(VERIFY_TOKEN);

            // when
            authService.remindPassword(new ForgotPasswordRequest(EMAIL));

            // then
            var eventCaptor = ArgumentCaptor.forClass(ResetPasswordEmailEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().email()).isEqualTo(EMAIL);
            assertThat(eventCaptor.getValue().token()).isEqualTo(VERIFY_TOKEN);
        }

        @Test
        @DisplayName("Ничего не делает, когда пользователь не найден")
        void doesNothingWhenUserNotFound() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            // when
            authService.remindPassword(new ForgotPasswordRequest(EMAIL));

            // then
            verifyNoInteractions(verificationTokenService, eventPublisher);
        }

        @Test
        @DisplayName("Ничего не делает, когда пользователь неактивен")
        void doesNothingWhenUserInactive() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(inactiveUser()));

            // when
            authService.remindPassword(new ForgotPasswordRequest(EMAIL));

            // then
            verifyNoInteractions(verificationTokenService, eventPublisher);
        }
    }

    // -------------------------------------------------------------------------
    // resetPassword
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ResetPassword")
    class ResetPassword {

        private static final String NEW_PASSWORD = "N3wP@ssw0rd";
        private static final String NEW_ENCODED = "$argon2id$new-encoded";

        @Test
        @DisplayName("Сбрасывает пароль и отзывает все сессии")
        void resetsPasswordAndRevokesAllSessions() {
            // given
            var user = activeVerifiedUser();
            when(verificationTokenService.consume(VERIFY_TOKEN, VerificationToken.Type.PASSWORD_RESET))
                    .thenReturn(user);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_ENCODED);

            // when
            authService.resetPassword(VERIFY_TOKEN, NEW_PASSWORD);

            // then
            // dirty checking: проверяем состояние объекта
            assertThat(user.getPassword()).isEqualTo(NEW_ENCODED);
            verify(refreshTokenService).revokeAll(user);
        }

        @Test
        @DisplayName("Прокидывает BadCredentialsException от VerificationTokenService")
        void propagatesExceptionFromVerificationTokenService() {
            // given
            when(verificationTokenService.consume(VERIFY_TOKEN, VerificationToken.Type.PASSWORD_RESET))
                    .thenThrow(new BadCredentialsException("Invalid token"));

            // when / then
            assertThatThrownBy(() -> authService.resetPassword(VERIFY_TOKEN, NEW_PASSWORD))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid token");

            verifyNoInteractions(passwordEncoder, refreshTokenService);
        }
    }

    // -------------------------------------------------------------------------
    // resendVerification
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ResendVerification")
    class ResendVerification {

        @Test
        @DisplayName("Повторно отправляет письмо верификации активному неверифицированному пользователю")
        void resendsVerificationEmailToActiveUnverifiedUser() {
            // given
            var user = activeUnverifiedUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(verificationTokenService.issue(user, VerificationToken.Type.EMAIL_VERIFICATION))
                    .thenReturn(VERIFY_TOKEN);

            // when
            authService.resendVerification(new ResendVerificationRequest(EMAIL));

            // then
            var eventCaptor = ArgumentCaptor.forClass(VerificationEmailEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().email()).isEqualTo(EMAIL);
            assertThat(eventCaptor.getValue().token()).isEqualTo(VERIFY_TOKEN);
        }

        @Test
        @DisplayName("Ничего не делает, когда пользователь не найден")
        void doesNothingWhenUserNotFound() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            // when
            authService.resendVerification(new ResendVerificationRequest(EMAIL));

            // then
            verifyNoInteractions(verificationTokenService, eventPublisher);
        }

        @Test
        @DisplayName("Ничего не делает, когда пользователь неактивен")
        void doesNothingWhenUserInactive() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(inactiveUser()));

            // when
            authService.resendVerification(new ResendVerificationRequest(EMAIL));

            // then
            verifyNoInteractions(verificationTokenService, eventPublisher);
        }

        @Test
        @DisplayName("Ничего не делает, когда email уже верифицирован")
        void doesNothingWhenEmailAlreadyVerified() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeVerifiedUser()));

            // when
            authService.resendVerification(new ResendVerificationRequest(EMAIL));

            // then
            verifyNoInteractions(verificationTokenService, eventPublisher);
        }
    }

    // -------------------------------------------------------------------------
    // deactivateUser
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DeactivateUser")
    class DeactivateUser {

        @Test
        @DisplayName("Деактивирует активного пользователя: active=false, deactivated != null")
        void deactivatesActiveUser() {
            // given
            var user = activeVerifiedUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            // when
            authService.deactivateUser(USER_ID);

            // then
            assertThat(user.isActive()).isFalse();
            assertThat(user.getDeactivated()).isNotNull().isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
            verify(refreshTokenService).revokeAll(user);
        }

        @Test
        @DisplayName("Ничего не делает, когда пользователь не найден")
        void doesNothingWhenUserNotFound() {
            // given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when
            authService.deactivateUser(USER_ID);

            // then
            verifyNoInteractions(refreshTokenService);
        }

        @Test
        @DisplayName("Ничего не делает, когда пользователь уже неактивен")
        void doesNothingWhenUserAlreadyInactive() {
            // given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(inactiveUser()));

            // when
            authService.deactivateUser(USER_ID);

            // then
            verifyNoInteractions(refreshTokenService);
        }
    }
}
