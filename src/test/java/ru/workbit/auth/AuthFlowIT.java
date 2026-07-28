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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.dto.*;
import ru.workbit.security.service.JWTService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(AuthTestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-enough-for-hmac",
        "jwt.expiration=3600000",
        "app.security.rate-limit.limit=1000",
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
    private static final String TRAINING_SESSIONS = "/api/v1/training/sessions";
    private static final String PASSWORD = "P@ssw0rd123";
    private static final String NEW_PASSWORD = "N3wP@ssw0rd!";
    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AuthTestConfig.TokenCaptor tokenCaptor;

    @Autowired
    JWTService jwtService;

    // -------------------------------------------------------------------------
    // Вспомогательные методы
    // -------------------------------------------------------------------------

    /**
     * Значения auth-cookie, извлечённые из Set-Cookie заголовков ответа.
     * Поля nullable — не каждый ответ выставляет обе cookie.
     */
    private record AuthCookies(String access, String refresh) {
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /**
     * Извлекает значение cookie с заданным именем из заголовков Set-Cookie ответа.
     */
    private String extractCookieValue(ResponseEntity<?> response, String cookieName) {
        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null) {
            return null;
        }
        return setCookieHeaders.stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .map(header -> header.substring(cookieName.length() + 1, header.indexOf(';')))
                .findFirst()
                .orElse(null);
    }

    /**
     * Достаёт из ответа Max-Age заданной cookie (для проверки очистки cookie при logout/delete).
     */
    private String extractCookieMaxAge(ResponseEntity<?> response, String cookieName) {
        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null) {
            return null;
        }
        return setCookieHeaders.stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .findFirst()
                .map(header -> {
                    int idx = header.toLowerCase().indexOf("max-age=");
                    if (idx < 0) {
                        return null;
                    }
                    int start = idx + "max-age=".length();
                    int end = header.indexOf(';', start);
                    return end < 0 ? header.substring(start) : header.substring(start, end);
                })
                .orElse(null);
    }

    private AuthCookies extractAuthCookies(ResponseEntity<?> response) {
        return new AuthCookies(
                extractCookieValue(response, ACCESS_COOKIE),
                extractCookieValue(response, REFRESH_COOKIE));
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
     * Регистрирует и верифицирует email, возвращает cookie access/refresh из ответа verify-email.
     */
    private AuthCookies registerAndVerify(String email) {
        register(email);
        String verifyToken = tokenCaptor.getVerificationToken(email);
        assertThat(verifyToken).isNotNull();
        var response = rest.postForEntity(
                BASE + "/verify-email",
                new VerifyEmailRequest(verifyToken),
                Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return extractAuthCookies(response);
    }

    /**
     * Строит заголовки с access-токеном в cookie.
     */
    private HttpHeaders accessCookieHeaders(String accessToken) {
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, ACCESS_COOKIE + "=" + accessToken);
        return headers;
    }

    /**
     * Строит заголовки с refresh-токеном в cookie.
     */
    private HttpHeaders refreshCookieHeaders(String refreshToken) {
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, REFRESH_COOKIE + "=" + refreshToken);
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
    }

    // =========================================================================
    // POST /verify-email
    // =========================================================================

    @Nested
    @DisplayName("VerifyEmail")
    class VerifyEmail {

        @Test
        @DisplayName("Подтверждает email и выдаёт cookie access_token и refresh_token")
        void verifiesEmailAndSetsCookies() {
            // given
            var email = uniqueEmail();
            register(email);
            var token = tokenCaptor.getVerificationToken(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/verify-email",
                    new VerifyEmailRequest(token),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNull();
            var cookies = extractAuthCookies(response);
            assertThat(cookies.access()).isNotBlank();
            assertThat(cookies.refresh()).isNotBlank();
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
            rest.postForEntity(BASE + "/verify-email", new VerifyEmailRequest(token), Void.class);

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
        @DisplayName("Выдаёт cookie access_token и refresh_token при корректных учётных данных")
        void setsCookiesOnSuccess() {
            // given
            var email = uniqueEmail();
            registerAndVerify(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, PASSWORD),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNull();
            var cookies = extractAuthCookies(response);
            assertThat(cookies.access()).isNotBlank();
            assertThat(cookies.refresh()).isNotBlank();
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
    }

    // =========================================================================
    // POST /refresh
    // =========================================================================

    @Nested
    @DisplayName("Refresh")
    class Refresh {

        @Test
        @DisplayName("Выдаёт новую пару cookie по валидному refresh-токену из cookie")
        void setsNewCookiesOnSuccess() {
            // given
            var email = uniqueEmail();
            var cookies = registerAndVerify(email);

            // when
            var response = rest.exchange(
                    BASE + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshCookieHeaders(cookies.refresh())),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var newCookies = extractAuthCookies(response);
            assertThat(newCookies.access()).isNotBlank();
            assertThat(newCookies.refresh()).isNotBlank();
        }

        @Test
        @DisplayName("Возвращает 401 при использовании отозванного refresh-токена")
        void returns401ForRevokedToken() {
            // given — получаем cookie, используем refresh (ротация: старый отзывается)
            var email = uniqueEmail();
            var cookies = registerAndVerify(email);
            rest.exchange(
                    BASE + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshCookieHeaders(cookies.refresh())),
                    Void.class);

            // when — пытаемся использовать уже отозванный старый refresh
            var response = rest.exchange(
                    BASE + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshCookieHeaders(cookies.refresh())),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401 для полностью невалидного refresh-токена")
        void returns401ForInvalidToken() {
            // when
            var response = rest.exchange(
                    BASE + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshCookieHeaders("completely-invalid-refresh-token")),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401, когда refresh-cookie отсутствует")
        void returns401WhenCookieMissing() {
            // when
            var response = rest.postForEntity(BASE + "/refresh", HttpEntity.EMPTY, Void.class);

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
        @DisplayName("Отзывает refresh-токен, гасит cookie, после чего refresh по нему даёт 401")
        void revokesRefreshTokenAndClearsCookies() {
            // given
            var email = uniqueEmail();
            var cookies = registerAndVerify(email);

            // when — выходим
            var logoutResponse = rest.exchange(
                    BASE + "/logout",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshCookieHeaders(cookies.refresh())),
                    Void.class);

            // then — logout прошёл, 204, куки погашены
            assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(extractCookieMaxAge(logoutResponse, ACCESS_COOKIE)).isEqualTo("0");
            assertThat(extractCookieMaxAge(logoutResponse, REFRESH_COOKIE)).isEqualTo("0");

            // and — refresh отозванным токеном даёт 401
            var refreshResponse = rest.exchange(
                    BASE + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshCookieHeaders(cookies.refresh())),
                    Void.class);
            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // =========================================================================
    // GET /me
    // =========================================================================

    @Nested
    @DisplayName("Me")
    class Me {

        @Test
        @DisplayName("Возвращает профиль пользователя по валидной access-cookie")
        void returnsProfileWithValidAccessCookie() {
            // given
            var email = uniqueEmail();
            var cookies = registerAndVerify(email);

            // when
            var response = rest.exchange(
                    BASE + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(accessCookieHeaders(cookies.access())),
                    UserResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().email()).isEqualTo(email);
        }

        @Test
        @DisplayName("Возвращает 401 без access-cookie")
        void returns401WithoutAccessCookie() {
            // when
            var response = rest.exchange(
                    BASE + "/me",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
            var cookies = registerAndVerify(email);

            // when
            var headers = accessCookieHeaders(cookies.access());
            var changeResponse = rest.exchange(
                    BASE + "/change-password",
                    HttpMethod.PATCH,
                    new HttpEntity<>(
                            new ChangePasswordRequest(PASSWORD, NEW_PASSWORD),
                            headers),
                    Void.class);

            // then — смена пароля прошла
            assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // and — старый refresh отозван
            var refreshResponse = rest.exchange(
                    BASE + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshCookieHeaders(cookies.refresh())),
                    Void.class);
            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // and — логин с новым паролем работает
            var loginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, NEW_PASSWORD),
                    Void.class);
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Возвращает 401 при неверном старом пароле")
        void returns401ForWrongOldPassword() {
            // given
            var email = uniqueEmail();
            var cookies = registerAndVerify(email);

            // when
            var response = rest.exchange(
                    BASE + "/change-password",
                    HttpMethod.PATCH,
                    new HttpEntity<>(
                            new ChangePasswordRequest("WrongOld123!", NEW_PASSWORD),
                            accessCookieHeaders(cookies.access())),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Возвращает 401 без access-cookie")
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
            var cookies = registerAndVerify(email);
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
            var refreshResponse = rest.exchange(
                    BASE + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshCookieHeaders(cookies.refresh())),
                    Void.class);
            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // and — вход с новым паролем работает
            var loginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, NEW_PASSWORD),
                    Void.class);
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
        @DisplayName("Удаляет пользователя физически, возвращает 204, гасит cookie и освобождает email")
        void deletesUserAndClearsCookies() {
            // given
            var email = uniqueEmail();
            var cookies = registerAndVerify(email);

            // when
            var response = rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(accessCookieHeaders(cookies.access())),
                    Void.class);

            // then — удаление прошло, куки погашены
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(extractCookieMaxAge(response, ACCESS_COOKIE)).isEqualTo("0");
            assertThat(extractCookieMaxAge(response, REFRESH_COOKIE)).isEqualTo("0");

            // and — повторный логин с теми же учётными данными даёт 401 (пользователя больше нет)
            var loginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, PASSWORD),
                    Void.class);
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // and — email освободился, регистрация с ним снова проходит успешно
            var registerResponse = rest.postForEntity(
                    BASE + "/register",
                    new RegistrationRequest(email, PASSWORD),
                    Void.class);
            assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(tokenCaptor.getVerificationToken(email)).isNotNull();
        }

        @Test
        @DisplayName("После удаления логин по email возвращает 401")
        void afterDeleteLoginReturns401() {
            // given
            var email = uniqueEmail();
            var cookies = registerAndVerify(email);
            rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(accessCookieHeaders(cookies.access())),
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
        @DisplayName("Возвращает 401 без access-cookie")
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
            var cookies = registerAndVerify(email);

            // when
            rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(accessCookieHeaders(cookies.access())),
                    Void.class);

            // then — refresh по старому токену даёт 401
            var refreshResponse = rest.exchange(
                    BASE + "/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshCookieHeaders(cookies.refresh())),
                    Void.class);
            assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("После удаления старый access-токен теряет доступ к тренировочным сессиям пользователя")
        void afterDeleteOldAccessTokenLosesAccessToSessions() {
            // given — токен действителен, доступ к списку сессий есть
            var email = uniqueEmail();
            var cookies = registerAndVerify(email);
            var beforeDelete = rest.exchange(
                    TRAINING_SESSIONS,
                    HttpMethod.GET,
                    new HttpEntity<>(accessCookieHeaders(cookies.access())),
                    Void.class);
            assertThat(beforeDelete.getStatusCode()).isEqualTo(HttpStatus.OK);

            // when — удаляем аккаунт
            rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(accessCookieHeaders(cookies.access())),
                    Void.class);

            // then — тем же access-токеном список сессий больше недоступен
            var afterDelete = rest.exchange(
                    TRAINING_SESSIONS,
                    HttpMethod.GET,
                    new HttpEntity<>(accessCookieHeaders(cookies.access())),
                    Void.class);
            assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Старый токен удалённого аккаунта не даёт доступ к новому аккаунту с тем же email")
        void oldAccessTokenOfDeletedAccountDoesNotAuthenticateNewAccountWithSameEmail() {
            // given — исходный пользователь верифицирован, сохраняем его access-токен
            var email = uniqueEmail();
            var oldCookies = registerAndVerify(email);
            var oldAccessToken = oldCookies.access();

            // when — аккаунт удалён, а тем же email зарегистрирован НОВЫЙ пользователь (другой UUID)
            rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(accessCookieHeaders(oldAccessToken)),
                    Void.class);
            var newCookies = registerAndVerify(email);

            // then — старый токен структурно валиден (не истёк по TTL), но принадлежит удалённому пользователю
            assertThat(jwtService.isTokenValid(oldAccessToken)).isTrue();

            // and — старым токеном /me не даёт доступа к новому аккаунту
            var meWithOldToken = rest.exchange(
                    BASE + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(accessCookieHeaders(oldAccessToken)),
                    UserResponse.class);
            assertThat(meWithOldToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(meWithOldToken.getBody()).isNull();

            // and — новым токеном /me корректно возвращает нового пользователя
            var meWithNewToken = rest.exchange(
                    BASE + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(accessCookieHeaders(newCookies.access())),
                    UserResponse.class);
            assertThat(meWithNewToken.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(meWithNewToken.getBody().email()).isEqualTo(email);
        }
    }

    // =========================================================================
    // Сквозные сценарии: полный цикл жизни пользователя
    // =========================================================================

    @Nested
    @DisplayName("FullLifecycleFlow")
    class FullLifecycleFlow {

        @Test
        @DisplayName("Регистрация -> верификация -> /me -> смена пароля -> логин с новым паролем")
        void registerVerifyMeChangePasswordLogin() {
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
                    Void.class);
            assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            var cookies = extractAuthCookies(verifyResponse);
            assertThat(cookies.access()).isNotBlank();
            assertThat(cookies.refresh()).isNotBlank();

            // me
            var meResponse = rest.exchange(
                    BASE + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(accessCookieHeaders(cookies.access())),
                    UserResponse.class);
            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(meResponse.getBody()).isNotNull();
            assertThat(meResponse.getBody().email()).isEqualTo(email);

            // change password
            var changeResponse = rest.exchange(
                    BASE + "/change-password",
                    HttpMethod.PATCH,
                    new HttpEntity<>(
                            new ChangePasswordRequest(PASSWORD, NEW_PASSWORD),
                            accessCookieHeaders(cookies.access())),
                    Void.class);
            assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // login with new password
            var newLoginResponse = rest.postForEntity(
                    BASE + "/login",
                    new LoginRequest(email, NEW_PASSWORD),
                    Void.class);
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
                    Void.class);
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
