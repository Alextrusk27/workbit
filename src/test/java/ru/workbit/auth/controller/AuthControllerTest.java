package ru.workbit.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.workbit.security.service.UserDetailsServiceImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.workbit.auth.dto.*;
import ru.workbit.auth.service.AuthCookieService;
import ru.workbit.auth.service.AuthService;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.TooManyRequestsException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.config.SecurityConfig;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;
import ru.workbit.security.service.RateLimiterService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ExceptionController.class, AuthCookieService.class})
@EnableConfigurationProperties(RateLimitProperties.class)
@DisplayName("AuthControllerTest")
class AuthControllerTest {

    private static final String BASE = "/api/v1/auth";
    private static final String EMAIL = "user@example.com";
    private static final String CODE = "123456";
    private static final String ACCESS_TOKEN = "jwt-access-token";
    private static final String REFRESH_TOKEN = "raw-refresh-token";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    MockMvc mvc;

    // ObjectMapper создаём напрямую: в SB4 @WebMvcTest не включает JacksonAutoConfiguration автоматически
    private final ObjectMapper om = new ObjectMapper();

    @MockitoBean
    AuthService authService;

    @MockitoBean
    RateLimiterService rateLimiterService;

    // JWTAuthFilter-зависимости: нужны, чтобы SecurityConfig мог создать фильтр
    @MockitoBean
    JWTService jwtService;

    @MockitoBean
    UserDetailsServiceImpl userDetailsService;

    // Вспомогательный метод для создания CustomUserDetails в тестах авторизации
    private CustomUserDetails principal() {
        return new CustomUserDetails(USER_ID, EMAIL, List.of());
    }

    private TokenResponse tokenResponse() {
        return new TokenResponse(ACCESS_TOKEN, REFRESH_TOKEN);
    }

    // -------------------------------------------------------------------------
    // POST /request-code
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("RequestCode")
    class RequestCode {

        @Test
        @DisplayName("Возвращает 200 при успешном запросе кода")
        void returnsOkOnSuccess() throws Exception {
            // given
            var request = new RequestCodeRequest(EMAIL);
            doNothing().when(authService).requestCode(any());

            // when / then
            mvc.perform(post(BASE + "/request-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService).requestCode(any());
        }

        @Test
        @DisplayName("Возвращает 400, когда email невалиден")
        void returns400WhenEmailInvalid() throws Exception {
            // given
            var request = new RequestCodeRequest("not-an-email");

            // when / then
            mvc.perform(post(BASE + "/request-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 429, когда превышен лимит запросов")
        void returns429WhenRateLimitExceeded() throws Exception {
            // given
            var request = new RequestCodeRequest(EMAIL);
            doThrow(new TooManyRequestsException("Too many requests")).when(rateLimiterService).check(anyString());

            // when / then
            mvc.perform(post(BASE + "/request-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value("TOO_MANY_REQUESTS"))
                    .andExpect(jsonPath("$.message").value("Too many requests."))
                    .andExpect(jsonPath("$.errors[0]").value("Too many requests"));

            verifyNoInteractions(authService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /verify-code
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("VerifyCode")
    class VerifyCode {

        @Test
        @DisplayName("Возвращает 200 и токены в cookie при верном коде")
        void returnsTokensOnSuccess() throws Exception {
            // given
            var request = new VerifyCodeRequest(EMAIL, CODE);
            when(authService.verifyCode(request)).thenReturn(tokenResponse());

            // when
            var result = mvc.perform(post(BASE + "/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").doesNotExist())
                    .andExpect(cookie().value(AuthCookieService.ACCESS_COOKIE_NAME, ACCESS_TOKEN))
                    .andExpect(cookie().httpOnly(AuthCookieService.ACCESS_COOKIE_NAME, true))
                    .andExpect(cookie().secure(AuthCookieService.ACCESS_COOKIE_NAME, true))
                    .andExpect(cookie().path(AuthCookieService.ACCESS_COOKIE_NAME, "/"))
                    .andExpect(cookie().value(AuthCookieService.REFRESH_COOKIE_NAME, REFRESH_TOKEN))
                    .andExpect(cookie().httpOnly(AuthCookieService.REFRESH_COOKIE_NAME, true))
                    .andExpect(cookie().secure(AuthCookieService.REFRESH_COOKIE_NAME, true))
                    .andExpect(cookie().path(AuthCookieService.REFRESH_COOKIE_NAME, BASE))
                    .andReturn();

            // then
            var setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
            assertThat(setCookieHeaders).allMatch(h -> h.contains("SameSite=Lax"));
        }

        @Test
        @DisplayName("Возвращает 401, когда код неверен")
        void returns401WhenInvalidCode() throws Exception {
            // given
            var request = new VerifyCodeRequest(EMAIL, CODE);
            when(authService.verifyCode(request)).thenThrow(new BadCredentialsException("Invalid code"));

            // when / then
            mvc.perform(post(BASE + "/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errors[0]").value("Invalid code"));
        }

        @Test
        @DisplayName("Возвращает 401, когда код истёк")
        void returns401WhenCodeExpired() throws Exception {
            // given
            var request = new VerifyCodeRequest(EMAIL, CODE);
            when(authService.verifyCode(request)).thenThrow(new BadCredentialsException("Code has expired"));

            // when / then
            mvc.perform(post(BASE + "/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errors[0]").value("Code has expired"));
        }

        @Test
        @DisplayName("Возвращает 401, когда исчерпаны попытки ввода кода")
        void returns401WhenTooManyAttempts() throws Exception {
            // given
            var request = new VerifyCodeRequest(EMAIL, CODE);
            when(authService.verifyCode(request)).thenThrow(new BadCredentialsException("Too many attempts"));

            // when / then
            mvc.perform(post(BASE + "/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errors[0]").value("Too many attempts"));
        }

        @Test
        @DisplayName("Возвращает 400, когда email невалиден")
        void returns400WhenEmailInvalid() throws Exception {
            // given
            var request = new VerifyCodeRequest("not-an-email", CODE);

            // when / then
            mvc.perform(post(BASE + "/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 400, когда код короче 6 цифр")
        void returns400WhenCodeTooShort() throws Exception {
            // given
            var request = new VerifyCodeRequest(EMAIL, "12345");

            // when / then
            mvc.perform(post(BASE + "/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 400, когда код содержит нецифровые символы")
        void returns400WhenCodeNotNumeric() throws Exception {
            // given
            var request = new VerifyCodeRequest(EMAIL, "12a456");

            // when / then
            mvc.perform(post(BASE + "/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 400, когда код пустой")
        void returns400WhenCodeBlank() throws Exception {
            // given
            var request = new VerifyCodeRequest(EMAIL, "");

            // when / then
            mvc.perform(post(BASE + "/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 429, когда превышен лимит запросов")
        void returns429WhenRateLimitExceeded() throws Exception {
            // given
            var request = new VerifyCodeRequest(EMAIL, CODE);
            doThrow(new TooManyRequestsException("Too many requests"))
                    .when(rateLimiterService).check(anyString(), any(RateLimitProperties.Bucket.class));

            // when / then
            mvc.perform(post(BASE + "/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value("TOO_MANY_REQUESTS"))
                    .andExpect(jsonPath("$.message").value("Too many requests."))
                    .andExpect(jsonPath("$.errors[0]").value("Too many requests"));

            verifyNoInteractions(authService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /refresh
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Refresh")
    class Refresh {

        @Test
        @DisplayName("Возвращает 200 и новые токены в cookie при валидном refresh-токене из cookie")
        void returnsNewTokensOnSuccess() throws Exception {
            // given
            when(authService.refresh(REFRESH_TOKEN)).thenReturn(tokenResponse());

            // when / then
            mvc.perform(post(BASE + "/refresh")
                            .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, REFRESH_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").doesNotExist())
                    .andExpect(cookie().value(AuthCookieService.ACCESS_COOKIE_NAME, ACCESS_TOKEN))
                    .andExpect(cookie().value(AuthCookieService.REFRESH_COOKIE_NAME, REFRESH_TOKEN));

            verify(authService).refresh(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("Возвращает 401, когда refresh-токен отозван или невалиден")
        void returns401WhenTokenRevoked() throws Exception {
            // given
            when(authService.refresh(REFRESH_TOKEN)).thenThrow(new BadCredentialsException("Token revoked"));

            // when / then
            mvc.perform(post(BASE + "/refresh")
                            .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, REFRESH_TOKEN)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Вызывает сервис с null, когда cookie refresh_token отсутствует")
        void callsServiceWithNullWhenCookieMissing() throws Exception {
            // given
            when(authService.refresh(null)).thenThrow(new BadCredentialsException("Token revoked"));

            // when / then — cookie не обязательна (required=false), сервис сам решает, что делать с null
            mvc.perform(post(BASE + "/refresh"))
                    .andExpect(status().isUnauthorized());

            verify(authService).refresh(null);
        }
    }

    // -------------------------------------------------------------------------
    // POST /logout
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Logout")
    class Logout {

        @Test
        @DisplayName("Возвращает 204 и гасит обе cookie при успешном выходе")
        void returnsNoContentOnSuccess() throws Exception {
            // given
            doNothing().when(authService).logout(REFRESH_TOKEN);

            // when / then
            mvc.perform(post(BASE + "/logout")
                            .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, REFRESH_TOKEN)))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().maxAge(AuthCookieService.ACCESS_COOKIE_NAME, 0))
                    .andExpect(cookie().maxAge(AuthCookieService.REFRESH_COOKIE_NAME, 0));

            verify(authService).logout(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("Возвращает 204 и гасит обе cookie, когда cookie refresh_token отсутствует (идемпотентный logout)")
        void clearsCookiesAndReturns204WhenCookieMissing() throws Exception {
            // when / then — @CookieValue необязательная; без cookie сервис не вызывается,
            // но обе cookie всё равно гасятся и ответ 204
            mvc.perform(post(BASE + "/logout"))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().maxAge(AuthCookieService.ACCESS_COOKIE_NAME, 0))
                    .andExpect(cookie().maxAge(AuthCookieService.REFRESH_COOKIE_NAME, 0));

            verifyNoInteractions(authService);
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /delete  (требует аутентификации)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DeleteAccount")
    class DeleteAccount {

        @Test
        @DisplayName("Возвращает 204, когда передан валидный принципал")
        void returns204WithPrincipal() throws Exception {
            // given
            doNothing().when(authService).deleteUser(USER_ID);

            // when / then
            mvc.perform(delete(BASE + "/delete")
                            .with(user(principal())))
                    .andExpect(status().isNoContent());

            verify(authService).deleteUser(USER_ID);
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // эндпоинт защищён (.authenticated()), без токена -> 401
            mvc.perform(delete(BASE + "/delete"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(authService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /me  (требует аутентификации)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Me")
    class Me {

        @Test
        @DisplayName("Возвращает 200 и профиль, когда передан валидный принципал")
        void returnsProfileWithPrincipal() throws Exception {
            // given
            var profile = new UserResponse(EMAIL, Instant.parse("2026-01-01T00:00:00Z"));
            when(authService.getProfile(USER_ID)).thenReturn(profile);

            // when / then
            mvc.perform(get(BASE + "/me")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(EMAIL));

            verify(authService).getProfile(USER_ID);
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then — эндпоинт защищён (.authenticated()), без токена -> 401
            mvc.perform(get(BASE + "/me"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(authService);
        }
    }
}
