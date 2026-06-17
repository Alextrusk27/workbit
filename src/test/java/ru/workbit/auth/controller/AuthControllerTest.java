package ru.workbit.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.workbit.auth.dto.*;
import ru.workbit.auth.service.AuthService;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.security.config.SecurityConfig;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ExceptionController.class})
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
        @DisplayName("Возвращает 200 и токены при успешном подтверждении")
        void returnsTokensOnSuccess() throws Exception {
            // given
            var request = new VerifyEmailRequest(TOKEN);
            when(authService.verifyEmail(TOKEN)).thenReturn(tokenResponse());

            // when / then
            mvc.perform(post(BASE + "/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                    .andExpect(jsonPath("$.refreshToken").value(REFRESH_TOKEN));
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
        @DisplayName("Возвращает 200 и токены при успешном входе")
        void returnsTokensOnSuccess() throws Exception {
            // given
            var request = new LoginRequest(EMAIL, PASSWORD);
            when(authService.login(any())).thenReturn(tokenResponse());

            // when / then
            mvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                    .andExpect(jsonPath("$.refreshToken").value(REFRESH_TOKEN));
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
        @DisplayName("Возвращает 200 и новые токены при валидном refresh-токене")
        void returnsNewTokensOnSuccess() throws Exception {
            // given
            var request = new RefreshRequest(REFRESH_TOKEN);
            when(authService.refresh(any())).thenReturn(tokenResponse());

            // when / then
            mvc.perform(post(BASE + "/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN));
        }

        @Test
        @DisplayName("Возвращает 401, когда refresh-токен отозван или невалиден")
        void returns401WhenTokenRevoked() throws Exception {
            // given
            var request = new RefreshRequest(REFRESH_TOKEN);
            when(authService.refresh(any())).thenThrow(new BadCredentialsException("Token revoked"));

            // when / then
            mvc.perform(post(BASE + "/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Возвращает 400, когда refreshToken пустой")
        void returns400WhenTokenBlank() throws Exception {
            // given
            var request = new RefreshRequest("  ");

            // when / then
            mvc.perform(post(BASE + "/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /logout
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Logout")
    class Logout {

        @Test
        @DisplayName("Возвращает 200 при успешном выходе")
        void returnsOkOnSuccess() throws Exception {
            // given
            var request = new LogoutRequest(REFRESH_TOKEN);
            doNothing().when(authService).logout(any());

            // when / then
            mvc.perform(post(BASE + "/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService).logout(any());
        }

        @Test
        @DisplayName("Возвращает 400, когда refreshToken пустой")
        void returns400WhenTokenBlank() throws Exception {
            // given
            var request = new LogoutRequest("");

            // when / then
            mvc.perform(post(BASE + "/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

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
}
