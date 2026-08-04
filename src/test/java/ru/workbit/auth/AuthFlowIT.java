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
import ru.workbit.auth.dto.RequestCodeRequest;
import ru.workbit.auth.dto.UserResponse;
import ru.workbit.auth.dto.VerifyCodeRequest;
import ru.workbit.auth.repository.LoginCodeJPARepository;
import ru.workbit.auth.repository.UserJPARepository;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.security.service.JWTService;

import java.time.Instant;
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
        "app.security.rate-limit.verify-code.limit=1000",
        "app.mail.from-name=Workbit",
        "app.mail.from-mail=noreply@workbit.ru",
        "app.mail.base-url=https://workbit.ru",
        "spring.mail.host=localhost",
        "spring.mail.port=25"
})
@DisplayName("AuthFlowIT")
class AuthFlowIT extends AbstractPostgresIT {

    private static final String BASE = "/api/v1/auth";
    private static final String TRAINING_SESSIONS = "/api/v1/training/sessions";
    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AuthTestConfig.CodeCaptor codeCaptor;

    @Autowired
    JWTService jwtService;

    @Autowired
    UserJPARepository userRepository;

    @Autowired
    LoginCodeJPARepository loginCodeRepository;

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
     * Запрашивает код входа на email (заводит пользователя, если его ещё не было) и возвращает email.
     */
    private String requestCode(String email) {
        var response = rest.postForEntity(
                BASE + "/request-code",
                new RequestCodeRequest(email),
                Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return email;
    }

    /**
     * Достаёт последний перехваченный код для email, требуя, чтобы он был выпущен.
     */
    private String codeFor(String email) {
        var code = codeCaptor.getCode(email);
        assertThat(code).isNotNull();
        return code;
    }

    /**
     * Строит заведомо неверный шестизначный код, отличный от переданного корректного.
     */
    private String wrongCodeFor(String correctCode) {
        int wrong = (Integer.parseInt(correctCode) + 1) % 1_000_000;
        return "%06d".formatted(wrong);
    }

    /**
     * Запрашивает код и подтверждает вход кодом из письма, возвращает cookie access/refresh из ответа verify-code.
     */
    private AuthCookies login(String email) {
        requestCode(email);
        var code = codeFor(email);
        var response = rest.postForEntity(
                BASE + "/verify-code",
                new VerifyCodeRequest(email, code),
                Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return extractAuthCookies(response);
    }

    /**
     * Помечает последний активный код пользователя истёкшим напрямую через репозитории — TTL не пройти иначе.
     */
    private void expireCode(String email) {
        var user = userRepository.findByEmail(email).orElseThrow();
        var code = loginCodeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user).orElseThrow();
        code.setExpiresAt(Instant.now().minusSeconds(60));
        loginCodeRepository.save(code);
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
    // POST /request-code
    // =========================================================================

    @Nested
    @DisplayName("RequestCode")
    class RequestCode {

        @Test
        @DisplayName("Первый запрос кода на незнакомый email создаёт пользователя и пускает его внутрь")
        void firstRequestCreatesUserAndLetsIn() {
            // given
            var email = uniqueEmail();

            // when
            var response = rest.postForEntity(
                    BASE + "/request-code",
                    new RequestCodeRequest(email),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var code = codeFor(email);

            // and — новосозданный пользователь проходит вход по полученному коду
            var verifyResponse = rest.postForEntity(
                    BASE + "/verify-code",
                    new VerifyCodeRequest(email, code),
                    Void.class);
            assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            var cookies = extractAuthCookies(verifyResponse);
            assertThat(cookies.access()).isNotBlank();
            assertThat(cookies.refresh()).isNotBlank();
        }

        @Test
        @DisplayName("Отправляет новый код для уже существующего пользователя")
        void sendsCodeForExistingUser() {
            // given
            var email = uniqueEmail();
            login(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/request-code",
                    new RequestCodeRequest(email),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(codeCaptor.getCode(email)).matches("\\d{6}");
        }

        @Test
        @DisplayName("Перевыдаёт код и делает прежний код недействительным")
        void reissuesCodeAndInvalidatesOldCode() {
            // given
            var email = uniqueEmail();
            requestCode(email);
            var oldCode = codeFor(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/request-code",
                    new RequestCodeRequest(email),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var newCode = codeFor(email);

            // and — старый код больше не подходит
            var oldCodeResponse = rest.postForEntity(
                    BASE + "/verify-code", new VerifyCodeRequest(email, oldCode), Void.class);
            assertThat(oldCodeResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // and — новый код даёт доступ
            var newCodeResponse = rest.postForEntity(
                    BASE + "/verify-code", new VerifyCodeRequest(email, newCode), Void.class);
            assertThat(newCodeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // =========================================================================
    // POST /verify-code
    // =========================================================================

    @Nested
    @DisplayName("VerifyCode")
    class VerifyCode {

        @Test
        @DisplayName("Подтверждает код и выдаёт cookie access_token и refresh_token")
        void verifiesCodeAndSetsCookies() {
            // given
            var email = uniqueEmail();
            requestCode(email);
            var code = codeFor(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/verify-code",
                    new VerifyCodeRequest(email, code),
                    Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNull();
            var cookies = extractAuthCookies(response);
            assertThat(cookies.access()).isNotBlank();
            assertThat(cookies.refresh()).isNotBlank();
        }

        @Test
        @DisplayName("Возвращает 401 при неверном коде")
        void returns401ForInvalidCode() {
            // given
            var email = uniqueEmail();
            requestCode(email);
            var wrongCode = wrongCodeFor(codeFor(email));

            // when
            var response = rest.postForEntity(
                    BASE + "/verify-code",
                    new VerifyCodeRequest(email, wrongCode),
                    ApiError.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().errors()).containsExactly("Invalid code");
        }

        @Test
        @DisplayName("Возвращает 401 при повторном использовании уже применённого кода")
        void returns401ForAlreadyUsedCode() {
            // given — используем код один раз
            var email = uniqueEmail();
            requestCode(email);
            var code = codeFor(email);
            rest.postForEntity(BASE + "/verify-code", new VerifyCodeRequest(email, code), Void.class);

            // when — пытаемся использовать тот же код снова
            var response = rest.postForEntity(
                    BASE + "/verify-code",
                    new VerifyCodeRequest(email, code),
                    ApiError.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().errors()).containsExactly("Invalid code");
        }

        @Test
        @DisplayName("Возвращает 401 при истёкшем коде")
        void returns401ForExpiredCode() {
            // given
            var email = uniqueEmail();
            requestCode(email);
            var code = codeFor(email);
            expireCode(email);

            // when
            var response = rest.postForEntity(
                    BASE + "/verify-code",
                    new VerifyCodeRequest(email, code),
                    ApiError.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().errors()).containsExactly("Code has expired");
        }

        @Test
        @DisplayName("После 5 неверных попыток код блокируется даже для верного значения")
        void locksAfterFiveWrongAttempts() {
            // given
            var email = uniqueEmail();
            requestCode(email);
            var code = codeFor(email);
            var wrongCode = wrongCodeFor(code);

            // when — пять неверных попыток подряд
            for (int attempt = 1; attempt <= 5; attempt++) {
                var response = rest.postForEntity(
                        BASE + "/verify-code",
                        new VerifyCodeRequest(email, wrongCode),
                        ApiError.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(response.getBody().errors()).containsExactly("Invalid code");
            }

            // then — шестая попытка заблокирована даже с верным кодом
            var response = rest.postForEntity(
                    BASE + "/verify-code",
                    new VerifyCodeRequest(email, code),
                    ApiError.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().errors()).containsExactly("Too many attempts");
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
            var cookies = login(email);

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
            var cookies = login(email);
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
            var cookies = login(email);

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
            var cookies = login(email);

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
            var cookies = login(email);

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

            // and — email освободился, запрос кода снова заводит пользователя и присылает код
            var requestCodeResponse = rest.postForEntity(
                    BASE + "/request-code",
                    new RequestCodeRequest(email),
                    Void.class);
            assertThat(requestCodeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(codeCaptor.getCode(email)).isNotNull();
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
            var cookies = login(email);

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
            var cookies = login(email);
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
            var oldCookies = login(email);
            var oldAccessToken = oldCookies.access();

            // when — аккаунт удалён, а тем же email заведён НОВЫЙ пользователь (другой UUID)
            rest.exchange(
                    BASE + "/delete",
                    HttpMethod.DELETE,
                    new HttpEntity<>(accessCookieHeaders(oldAccessToken)),
                    Void.class);
            var newCookies = login(email);

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
        @DisplayName("Запрос кода -> ввод кода -> /me -> повторный вход по новому коду")
        void requestCodeVerifyMeLoginAgainWithNewCode() {
            // given
            var email = uniqueEmail();

            // request code (заводит пользователя, так как email ещё не встречался)
            var requestCodeResponse = rest.postForEntity(
                    BASE + "/request-code",
                    new RequestCodeRequest(email),
                    Void.class);
            assertThat(requestCodeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // verify code
            var code = codeFor(email);
            var verifyResponse = rest.postForEntity(
                    BASE + "/verify-code",
                    new VerifyCodeRequest(email, code),
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

            // request a new login code (repeated login)
            var secondRequestCodeResponse = rest.postForEntity(
                    BASE + "/request-code",
                    new RequestCodeRequest(email),
                    Void.class);
            assertThat(secondRequestCodeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            var newCode = codeFor(email);

            // login again with the new code
            var secondLoginResponse = rest.postForEntity(
                    BASE + "/verify-code",
                    new VerifyCodeRequest(email, newCode),
                    Void.class);
            assertThat(secondLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            var newCookies = extractAuthCookies(secondLoginResponse);
            assertThat(newCookies.access()).isNotBlank();
            assertThat(newCookies.refresh()).isNotBlank();
        }
    }
}
