package ru.workbit.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.workbit.auth.controller.AuthController;
import ru.workbit.auth.dto.*;
import ru.workbit.auth.service.AuthCookieService;
import ru.workbit.auth.service.AuthService;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.security.config.SecurityConfig;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ExceptionController.class, AuthCookieService.class})
@DisplayName("AuthControllerTest")
class AuthControllerTest {

    private static final String BASE = "/api/v1/auth";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "P@ssw0rd123";
    private static final String TOKEN = "some-token-value";
    private static final String ACCESS_TOKEN = "jwt-access-token";
    private static final String REFRESH_TOKEN = "raw-refresh-token";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    MockMvc mvc;

    // ObjectMapper создаём напрямую: в SB4 @WebMvcTest не включает JacksonAutoConfiguration автоматически
    private final ObjectMapper om = new ObjectMapper();

    @MockitoBean
    AuthService authService;

    // JWTAuthFilter-зависимости: нужны, чтобы SecurityConfig мог создать фильтр
    @MockitoBean
    JWTService jwtService;

    @MockitoBean
    UserDetailsService userDetailsService;

    // Вспомогательный метод для создания CustomUserDetails в тестах авторизации
    private CustomUserDetails principal() {
        return new CustomUserDetails(USER_ID, EMAIL, PASSWORD, true, List.of());
    }

    private TokenResponse tokenResponse() {
        return new TokenResponse(ACCESS_TOKEN, REFRESH_TOKEN);
    }

    // -------------------------------------------------------------------------
    // POST /register
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        @DisplayName("Возвращает 200 при успешной регистрации")
        void returnsOkOnSuccess() throws Exception {
            // given
            var request = new RegistrationRequest(EMAIL, PASSWORD);
            doNothing().when(authService).register(any());

            // when / then
            mvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService).register(any());
        }

        @Test
        @DisplayName("Возвращает 401, когда email уже используется")
        void returns401WhenEmailAlreadyInUse() throws Exception {
            // given
            var request = new RegistrationRequest(EMAIL, PASSWORD);
            doThrow(new BadCredentialsException("Email already in use")).when(authService).register(any());

            // when / then
            mvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Bad credentials"));
        }

        @Test
        @DisplayName("Возвращает 400, когда email невалиден")
        void returns400WhenEmailInvalid() throws Exception {
            // given
            var request = new RegistrationRequest("not-an-email", PASSWORD);

            // when / then
            mvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 400, когда пароль короче 8 символов")
        void returns400WhenPasswordTooShort() throws Exception {
            // given
            var request = new RegistrationRequest(EMAIL, "short");

            // when / then
            mvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 400, когда тело запроса отсутствует")
        void returns400WhenBodyMissing() throws Exception {
            mvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------------------------
    // POST /verify-email
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("VerifyEmail")
    class VerifyEmail {

        @Test
        @DisplayName("Возвращает 200 и токены в cookie при успешном подтверждении")
        void returnsTokensOnSuccess() throws Exception {
            // given
            var request = new VerifyEmailRequest(TOKEN);
            when(authService.verifyEmail(TOKEN)).thenReturn(tokenResponse());

            // when
            var result = mvc.perform(post(BASE + "/verify-email")
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
        @DisplayName("Возвращает 401, когда токен недействителен")
        void returns401WhenTokenInvalid() throws Exception {
            // given
            var request = new VerifyEmailRequest(TOKEN);
            when(authService.verifyEmail(TOKEN)).thenThrow(new BadCredentialsException("Invalid token"));

            // when / then
            mvc.perform(post(BASE + "/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Возвращает 400, когда token пустой")
        void returns400WhenTokenBlank() throws Exception {
            // given
            var request = new VerifyEmailRequest("");

            // when / then
            mvc.perform(post(BASE + "/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /resend-verification
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ResendVerification")
    class ResendVerification {

        @Test
        @DisplayName("Возвращает 200 всегда (даже если email не найден)")
        void alwaysReturns200() throws Exception {
            // given
            var request = new ResendVerificationRequest(EMAIL);
            doNothing().when(authService).resendVerification(any());

            // when / then
            mvc.perform(post(BASE + "/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService).resendVerification(any());
        }

        @Test
        @DisplayName("Возвращает 400, когда email невалиден")
        void returns400WhenEmailInvalid() throws Exception {
            // given
            var request = new ResendVerificationRequest("bad-email");

            // when / then
            mvc.perform(post(BASE + "/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /login
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("Возвращает 200 и токены в cookie при успешном входе")
        void returnsTokensOnSuccess() throws Exception {
            // given
            var request = new LoginRequest(EMAIL, PASSWORD);
            when(authService.login(any())).thenReturn(tokenResponse());

            // when / then
            mvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").doesNotExist())
                    .andExpect(cookie().value(AuthCookieService.ACCESS_COOKIE_NAME, ACCESS_TOKEN))
                    .andExpect(cookie().path(AuthCookieService.ACCESS_COOKIE_NAME, "/"))
                    .andExpect(cookie().value(AuthCookieService.REFRESH_COOKIE_NAME, REFRESH_TOKEN))
                    .andExpect(cookie().path(AuthCookieService.REFRESH_COOKIE_NAME, BASE));
        }

        @Test
        @DisplayName("Возвращает 401, когда учётные данные неверны")
        void returns401WhenBadCredentials() throws Exception {
            // given
            var request = new LoginRequest(EMAIL, PASSWORD);
            when(authService.login(any())).thenThrow(new BadCredentialsException("Invalid credentials"));

            // when / then
            mvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Bad credentials"));
        }

        @Test
        @DisplayName("Возвращает 400, когда email отсутствует")
        void returns400WhenEmailMissing() throws Exception {
            // given — email=null
            var request = new LoginRequest(null, PASSWORD);

            // when / then
            mvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 400, когда пароль короче 8 символов")
        void returns400WhenPasswordTooShort() throws Exception {
            // given
            var request = new LoginRequest(EMAIL, "short");

            // when / then
            mvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

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
    // PATCH /change-password  (требует аутентификации)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ChangePassword")
    class ChangePassword {

        @Test
        @DisplayName("Возвращает 200, когда передан валидный принципал")
        void returnsOkWithPrincipal() throws Exception {
            // given
            var request = new ChangePasswordRequest("OldP@ss123", "NewP@ss123");
            doNothing().when(authService).changePassword(any(), eq(USER_ID));

            // when / then
            mvc.perform(patch(BASE + "/change-password")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService).changePassword(any(), eq(USER_ID));
        }

        @Test
        @DisplayName("Возвращает 401, когда старый пароль неверен")
        void returns401WhenOldPasswordWrong() throws Exception {
            // given
            var request = new ChangePasswordRequest("WrongOld1", "NewP@ss123");
            doThrow(new BadCredentialsException("Invalid credentials"))
                    .when(authService).changePassword(any(), any());

            // when / then
            mvc.perform(patch(BASE + "/change-password")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет токена (нет аутентификации)")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var request = new ChangePasswordRequest("OldP@ss123", "NewP@ss123");

            // when / then — эндпоинт защищён (.authenticated()), без токена -> 401
            mvc.perform(patch(BASE + "/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 400, когда newPassword короче 8 символов")
        void returns400WhenNewPasswordTooShort() throws Exception {
            // given
            var request = new ChangePasswordRequest("OldP@ss123", "short");

            // when / then
            mvc.perform(patch(BASE + "/change-password")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /forgot-password
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ForgotPassword")
    class ForgotPassword {

        @Test
        @DisplayName("Возвращает 200 всегда (не раскрывает наличие email)")
        void alwaysReturns200() throws Exception {
            // given
            var request = new ForgotPasswordRequest(EMAIL);
            doNothing().when(authService).remindPassword(any());

            // when / then
            mvc.perform(post(BASE + "/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService).remindPassword(any());
        }

        @Test
        @DisplayName("Возвращает 400, когда email невалиден")
        void returns400WhenEmailInvalid() throws Exception {
            // given
            var request = new ForgotPasswordRequest("not-email");

            // when / then
            mvc.perform(post(BASE + "/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /reset-password
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ResetPassword")
    class ResetPassword {

        @Test
        @DisplayName("Возвращает 200 при успешном сбросе пароля")
        void returnsOkOnSuccess() throws Exception {
            // given
            var request = new ResetPasswordRequest(TOKEN, "NewP@ss123");
            doNothing().when(authService).resetPassword(TOKEN, "NewP@ss123");

            // when / then
            mvc.perform(post(BASE + "/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService).resetPassword(TOKEN, "NewP@ss123");
        }

        @Test
        @DisplayName("Возвращает 401, когда токен недействителен")
        void returns401WhenTokenInvalid() throws Exception {
            // given
            var request = new ResetPasswordRequest(TOKEN, "NewP@ss123");
            doThrow(new BadCredentialsException("Invalid token"))
                    .when(authService).resetPassword(any(), any());

            // when / then
            mvc.perform(post(BASE + "/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Возвращает 400, когда newPassword короче 8 символов")
        void returns400WhenPasswordTooShort() throws Exception {
            // given
            var request = new ResetPasswordRequest(TOKEN, "short");

            // when / then
            mvc.perform(post(BASE + "/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Возвращает 400, когда token пустой")
        void returns400WhenTokenBlank() throws Exception {
            // given
            var request = new ResetPasswordRequest("", "NewP@ss123");

            // when / then
            mvc.perform(post(BASE + "/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

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
            doNothing().when(authService).deactivateUser(USER_ID);

            // when / then
            mvc.perform(delete(BASE + "/delete")
                            .with(user(principal())))
                    .andExpect(status().isNoContent());

            verify(authService).deactivateUser(USER_ID);
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
