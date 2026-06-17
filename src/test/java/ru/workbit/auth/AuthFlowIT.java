package ru.workbit.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.dto.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(AuthTestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-enough-for-hmac",
        "jwt.expiration=3600000",
        "app.mail.from-name=Workbit",
        "app.mail.from-mail=noreply@workbit.ru",
        "app.mail.base-url=https://workbit.ru",
        "app.mail.reset-ttl-minutes=15",
        "spring.mail.host=localhost",
        "spring.mail.port=25"
})
@DisplayName("AuthFlowIT")
class AuthFlowIT extends AbstractPostgresIT {

    private static final String BASE = "/api/v1/auth";
    private static final String PASSWORD = "P@ssw0rd123";
    private static final String NEW_PASSWORD = "N3wP@ssw0rd!";

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AuthTestConfig.TokenCaptor tokenCaptor;

    // -------------------------------------------------------------------------
    // Вспомогательные методы
    // -------------------------------------------------------------------------

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /**
     * Регистрирует пользователя и возвращает его email.
     */
    private String register(String email) {
        var response = rest.postForEntity(
                BASE + "/register",
                new RegistrationRequest(email, PASSWORD),
                Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return email;
    }

    /**
     * Регистрирует и верифицирует email, возвращает пару токенов.
     */
    private TokenResponse registerAndVerify(String email) {
        register(email);
        String verifyToken = tokenCaptor.getVerificationToken(email);
        assertThat(verifyToken).isNotNull();
        var response = rest.postForEntity(
                BASE + "/verify-email",
                new VerifyEmailRequest(verifyToken),
                TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    /**
     * Строит заголовки с Bearer access-токеном.
     */
    private HttpHeaders bearerHeaders(String accessToken) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    // =========================================================================
    // POST /register
    // =========================================================================

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        @DisplayName("Создаёт нового пользователя и возвращает 200")
        void createsNewUser() {
            // given
            var email = uniqueEmail();

            // when
            var response = rest.postForEntity(
                    BASE + "/register",
                    new RegistrationRequest(email, PASSWORD),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(tokenCaptor.getVerificationToken(email)).isNotNull();
        }

        @Test
        @DisplayName("Возвращает 401, когда email уже занят активным пользователем")
        void returns401WhenEmailAlreadyActive() {
            // given — зарегистрированный и верифицированный пользователь
            var email = uniqueEmail();
            registerAndVerify(email);

            // when — повторная регистрация с тем же email
            var response = rest.postForEntity(
                    BASE + "/register",
                    new RegistrationRequest(email, PASSWORD),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Реактивирует деактивированного пользователя и возвращает 200")
        void reactivatesDeactivatedUser() {
            // given — зарегистрировать, верифицировать, залогиниться, удалить
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);
            rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerHeaders(tokens.accessToken())),
                    Void.class);

            // when — повторная регистрация с тем же email
            var response = rest.postForEntity(
                    BASE + "/register",
                    new RegistrationRequest(email, NEW_PASSWORD),
                    Void.class);

            // then — реактивация, 200, новый токен верификации
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(tokenCaptor.getVerificationToken(email)).isNotNull();
        }
    }

    // =========================================================================
    // POST /verify-email
    // =========================================================================

    @Nested
    @DisplayName("VerifyEmail")
    class VerifyEmail {

        @Test
        @DisplayName("Подтверждает email и выдаёт пару токенов")
        void verifiesEmailAndReturnsTokens() {
            // given
            var email = uniqueEmail();
            register(email);
            var token = tokenCaptor.getVerificationToken(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/verify-email",
                    new VerifyEmailRequest(token),
                    TokenResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().accessToken()).isNotBlank();
            assertThat(response.getBody().refreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("Возвращает 401 при использовании невалидного токена")
        void returns401ForInvalidToken() {
            // when
            var response = rest.postForEntity(
                    BASE + "/verify-email",
                    new VerifyEmailRequest("totally-invalid-token-xyz"),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401 при повторном использовании уже применённого токена")
        void returns401ForAlreadyUsedToken() {
            // given — верифицируем один раз
            var email = uniqueEmail();
            register(email);
            var token = tokenCaptor.getVerificationToken(email);
            rest.postForEntity(BASE + "/verify-email", new VerifyEmailRequest(token), TokenResponse.class);

            // when — пытаемся использовать тот же токен снова
            var response = rest.postForEntity(
                    BASE + "/verify-email",
                    new VerifyEmailRequest(token),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // =========================================================================
    // POST /resend-verification
    // =========================================================================

    @Nested
    @DisplayName("ResendVerification")
    class ResendVerification {

        @Test
        @DisplayName("Возвращает 200 для существующего неверифицированного пользователя")
        void returns200ForExistingUnverifiedUser() {
            // given
            var email = uniqueEmail();
            register(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/resend-verification",
                    new ResendVerificationRequest(email),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(tokenCaptor.getVerificationToken(email)).isNotNull();
        }

        @Test
        @DisplayName("Возвращает 200 даже для несуществующего email")
        void returns200ForNonExistentEmail() {
            // when
            var response = rest.postForEntity(
                    BASE + "/resend-verification",
                    new ResendVerificationRequest("nobody@example.com"),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // =========================================================================
    // POST /login
    // =========================================================================

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("Выдаёт токены при корректных учётных данных")
        void returnsTokensOnSuccess() {
            // given
            var email = uniqueEmail();
            registerAndVerify(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, PASSWORD),
                    TokenResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().accessToken()).isNotBlank();
            assertThat(response.getBody().refreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("Возвращает 401 при неверном пароле")
        void returns401ForWrongPassword() {
            // given
            var email = uniqueEmail();
            registerAndVerify(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, "WrongPass123!"),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401, когда email не подтверждён")
        void returns401WhenEmailNotVerified() {
            // given — зарегистрирован, но не верифицирован
            var email = uniqueEmail();
            register(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, PASSWORD),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401 для деактивированного пользователя")
        void returns401ForDeactivatedUser() {
            // given — зарегистрирован, верифицирован, затем деактивирован
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);
            rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerHeaders(tokens.accessToken())),
                    Void.class);

            // when — пытаемся войти
            var response = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, PASSWORD),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // =========================================================================
    // POST /refresh
    // =========================================================================

    @Nested
    @DisplayName("Refresh")
    class Refresh {

        @Test
        @DisplayName("Выдаёт новую пару токенов по валидному refresh-токену")
        void returnsNewTokensOnSuccess() {
            // given
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/refresh",
                    new RefreshRequest(tokens.refreshToken()),
                    TokenResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().accessToken()).isNotBlank();
            assertThat(response.getBody().refreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("Возвращает 401 при использовании отозванного refresh-токена")
        void returns401ForRevokedToken() {
            // given — получаем токены, используем refresh (ротация: старый отзывается)
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);
            rest.postForEntity(BASE + "/refresh", new RefreshRequest(tokens.refreshToken()), TokenResponse.class);

            // when — пытаемся использовать уже отозванный старый refresh
            var response = rest.postForEntity(
                    BASE + "/refresh",
                    new RefreshRequest(tokens.refreshToken()),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401 для полностью невалидного refresh-токена")
        void returns401ForInvalidToken() {
            // when
            var response = rest.postForEntity(
                    BASE + "/refresh",
                    new RefreshRequest("completely-invalid-refresh-token"),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // =========================================================================
    // POST /logout
    // =========================================================================

    @Nested
    @DisplayName("Logout")
    class Logout {

        @Test
        @DisplayName("Отзывает refresh-токен, после чего refresh по нему даёт 401")
        void revokesRefreshTokenAndPreventsSubsequentRefresh() {
            // given
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);

            // when — выходим
            var logoutResponse = rest.postForEntity(
                    BASE + "/logout",
                    new LogoutRequest(tokens.refreshToken()),
                    Void.class);

            // then — logout прошёл
            assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // and — refresh отозванным токеном даёт 401
            var refreshResponse = rest.postForEntity(
                    BASE + "/refresh",
                    new RefreshRequest(tokens.refreshToken()),
                    Void.class);
            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // =========================================================================
    // PATCH /change-password
    // =========================================================================

    @Nested
    @DisplayName("ChangePassword")
    class ChangePassword {

        @Test
        @DisplayName("Меняет пароль и отзывает все refresh-токены")
        void changesPasswordAndRevokesRefreshTokens() {
            // given
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);

            // when
            var changeResponse = rest.exchange(
                    BASE + "/change-password",
                    HttpMethod.PATCH,
                    new HttpEntity<>(
                            new ChangePasswordRequest(PASSWORD, NEW_PASSWORD),
                            bearerHeaders(tokens.accessToken())),
                    Void.class);

            // then — смена пароля прошла
            assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // and — старый refresh отозван
            var refreshResponse = rest.postForEntity(
                    BASE + "/refresh",
                    new RefreshRequest(tokens.refreshToken()),
                    Void.class);
            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // and — логин с новым паролем работает
            var loginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, NEW_PASSWORD),
                    TokenResponse.class);
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Возвращает 401 при неверном старом пароле")
        void returns401ForWrongOldPassword() {
            // given
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);

            // when
            var response = rest.exchange(
                    BASE + "/change-password",
                    HttpMethod.PATCH,
                    new HttpEntity<>(
                            new ChangePasswordRequest("WrongOld123!", NEW_PASSWORD),
                            bearerHeaders(tokens.accessToken())),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401 без access-токена")
        void returns401WithoutToken() {
            // when — запрос без авторизации
            var response = rest.exchange(
                    BASE + "/change-password",
                    HttpMethod.PATCH,
                    new HttpEntity<>(new ChangePasswordRequest(PASSWORD, NEW_PASSWORD)),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // =========================================================================
    // POST /forgot-password
    // =========================================================================

    @Nested
    @DisplayName("ForgotPassword")
    class ForgotPassword {

        @Test
        @DisplayName("Возвращает 200 для существующего пользователя и генерирует токен сброса")
        void returns200AndGeneratesResetToken() {
            // given
            var email = uniqueEmail();
            registerAndVerify(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/forgot-password",
                    new ForgotPasswordRequest(email),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(tokenCaptor.getResetToken(email)).isNotNull();
        }

        @Test
        @DisplayName("Возвращает 200 даже для несуществующего email (не раскрывает наличие)")
        void returns200ForNonExistentEmail() {
            // when
            var response = rest.postForEntity(
                    BASE + "/forgot-password",
                    new ForgotPasswordRequest("nobody@example.com"),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // =========================================================================
    // POST /reset-password
    // =========================================================================

    @Nested
    @DisplayName("ResetPassword")
    class ResetPassword {

        @Test
        @DisplayName("Сбрасывает пароль и позволяет войти с новым паролем")
        void resetsPasswordAndAllowsLoginWithNewPassword() {
            // given — получаем токен сброса
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);
            rest.postForEntity(BASE + "/forgot-password", new ForgotPasswordRequest(email), Void.class);
            var resetToken = tokenCaptor.getResetToken(email);
            assertThat(resetToken).isNotNull();

            // when — сбрасываем пароль
            var resetResponse = rest.postForEntity(
                    BASE + "/reset-password",
                    new ResetPasswordRequest(resetToken, NEW_PASSWORD),
                    Void.class);

            // then — сброс прошёл
            assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // and — старый refresh отозван
            var refreshResponse = rest.postForEntity(
                    BASE + "/refresh",
                    new RefreshRequest(tokens.refreshToken()),
                    Void.class);
            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // and — вход с новым паролем работает
            var loginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, NEW_PASSWORD),
                    TokenResponse.class);
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Возвращает 401 при использовании невалидного токена сброса")
        void returns401ForInvalidToken() {
            // when
            var response = rest.postForEntity(
                    BASE + "/reset-password",
                    new ResetPasswordRequest("bad-reset-token-xyz", NEW_PASSWORD),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401 при повторном использовании уже применённого токена сброса")
        void returns401ForAlreadyUsedResetToken() {
            // given — используем токен один раз
            var email = uniqueEmail();
            registerAndVerify(email);
            rest.postForEntity(BASE + "/forgot-password", new ForgotPasswordRequest(email), Void.class);
            var resetToken = tokenCaptor.getResetToken(email);
            rest.postForEntity(BASE + "/reset-password", new ResetPasswordRequest(resetToken, NEW_PASSWORD), Void.class);

            // when — пытаемся применить тот же токен ещё раз
            var response = rest.postForEntity(
                    BASE + "/reset-password",
                    new ResetPasswordRequest(resetToken, "AnotherPass123!"),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // =========================================================================
    // DELETE /delete
    // =========================================================================

    @Nested
    @DisplayName("DeleteAccount")
    class DeleteAccount {

        @Test
        @DisplayName("Деактивирует пользователя (soft delete) и возвращает 204")
        void deactivatesUserAndReturns204() {
            // given
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);

            // when
            var response = rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerHeaders(tokens.accessToken())),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("После удаления логин по email возвращает 401")
        void afterDeleteLoginReturns401() {
            // given
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);
            rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerHeaders(tokens.accessToken())),
                    Void.class);

            // when
            var loginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, PASSWORD),
                    Void.class);

            // then
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401 без access-токена")
        void returns401WithoutToken() {
            // when
            var response = rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("После удаления все refresh-токены отозваны")
        void afterDeleteAllRefreshTokensRevoked() {
            // given
            var email = uniqueEmail();
            var tokens = registerAndVerify(email);

            // when
            rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerHeaders(tokens.accessToken())),
                    Void.class);

            // then — refresh по старому токену даёт 401
            var refreshResponse = rest.postForEntity(
                    BASE + "/refresh",
                    new RefreshRequest(tokens.refreshToken()),
                    Void.class);
            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // =========================================================================
    // Сквозные сценарии: полный цикл жизни пользователя
    // =========================================================================

    @Nested
    @DisplayName("FullLifecycleFlow")
    class FullLifecycleFlow {

        @Test
        @DisplayName("Регистрация -> верификация -> логин -> смена пароля -> логин с новым паролем")
        void registerVerifyLoginChangePasswordLogin() {
            // given
            var email = uniqueEmail();

            // register
            var registerResponse = rest.postForEntity(
                    BASE + "/register",
                    new RegistrationRequest(email, PASSWORD),
                    Void.class);
            assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // verify email
            var verifyToken = tokenCaptor.getVerificationToken(email);
            var verifyResponse = rest.postForEntity(
                    BASE + "/verify-email",
                    new VerifyEmailRequest(verifyToken),
                    TokenResponse.class);
            assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            var tokens = verifyResponse.getBody();
            assertThat(tokens).isNotNull();

            // login
            var loginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, PASSWORD),
                    TokenResponse.class);
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            var loginTokens = loginResponse.getBody();
            assertThat(loginTokens).isNotNull();

            // change password
            var changeResponse = rest.exchange(
                    BASE + "/change-password",
                    HttpMethod.PATCH,
                    new HttpEntity<>(
                            new ChangePasswordRequest(PASSWORD, NEW_PASSWORD),
                            bearerHeaders(loginTokens.accessToken())),
                    Void.class);
            assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // login with new password
            var newLoginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, NEW_PASSWORD),
                    TokenResponse.class);
            assertThat(newLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Forgot-password -> reset -> логин с новым паролем")
        void forgotPasswordResetLogin() {
            // given
            var email = uniqueEmail();
            registerAndVerify(email);

            // forgot password
            rest.postForEntity(BASE + "/forgot-password", new ForgotPasswordRequest(email), Void.class);
            var resetToken = tokenCaptor.getResetToken(email);
            assertThat(resetToken).isNotNull();

            // reset password
            var resetResponse = rest.postForEntity(
                    BASE + "/reset-password",
                    new ResetPasswordRequest(resetToken, NEW_PASSWORD),
                    Void.class);
            assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // login with new password
            var loginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, NEW_PASSWORD),
                    TokenResponse.class);
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
