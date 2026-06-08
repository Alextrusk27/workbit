package ru.workbit.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.workbit.dto.auth.ChangePasswordRequest;
import ru.workbit.dto.auth.LoginRequest;
import ru.workbit.dto.auth.RegistrationRequest;
import ru.workbit.dto.auth.TokenResponse;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.security.service.JWTService;
import ru.workbit.user.model.User;
import ru.workbit.user.repository.UserJPARepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceTest")
class AuthServiceTest {

    private static final String EMAIL = "user@workbit.ru";
    private static final String RAW_PASSWORD = "rawPassword";
    private static final String ENCODED_PASSWORD = "encodedPassword";
    private static final String TOKEN = "jwt-token";
    private static final String BEARER = "Bearer";

    @Mock
    UserJPARepository userRepository;
    @Mock
    JWTService jwtService;
    @Mock
    PasswordEncoder passwordEncoder;
    @InjectMocks
    AuthService service;

    @Captor
    ArgumentCaptor<User> userCaptor;

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        @DisplayName("Кодирует пароль, сохраняет пользователя и возвращает токен")
        void encodesPasswordSavesUserAndReturnsToken() {
            var request = new RegistrationRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateToken(any(User.class))).thenReturn(TOKEN);

            TokenResponse response = service.register(request);

            assertThat(response.accessToken()).isEqualTo(TOKEN);
            assertThat(response.tokenType()).isEqualTo(BEARER);

            verify(userRepository).save(userCaptor.capture());
            User saved = userCaptor.getValue();
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда email уже занят")
        void throwsWhenEmailAlreadyInUse() {
            var request = new RegistrationRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Email already in use");

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("Возвращает токен при верных учётных данных")
        void returnsTokenForValidCredentials() {
            var request = new LoginRequest(EMAIL, RAW_PASSWORD);
            User user = User.builder().email(EMAIL).password(ENCODED_PASSWORD).build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtService.generateToken(user)).thenReturn(TOKEN);

            TokenResponse response = service.login(request);

            assertThat(response.accessToken()).isEqualTo(TOKEN);
            assertThat(response.tokenType()).isEqualTo(BEARER);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда пользователь не найден")
        void throwsWhenUserNotFound() {
            var request = new LoginRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");
        }

        @Test
        @DisplayName("Бросает BadCredentialsException при неверном пароле")
        void throwsWhenPasswordIsWrong() {
            var request = new LoginRequest(EMAIL, "wrongPassword");
            User user = User.builder().email(EMAIL).password(ENCODED_PASSWORD).build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPassword", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> service.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда пользователь деактивирован")
        void throwsWhenUserIsNotActive() {
            var request = new LoginRequest(EMAIL, RAW_PASSWORD);
            User user = User.builder().email(EMAIL).password(ENCODED_PASSWORD).active(false).build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            assertThatThrownBy(() -> service.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("User was deactivated");

            verify(jwtService, never()).generateToken(any());
        }
    }

    @Nested
    @DisplayName("ChangePassword")
    class ChangePassword {

        private static final String OLD_RAW = "oldRaw";
        private static final String OLD_ENCODED = "oldEncoded";
        private static final String NEW_RAW = "newRawPassword";
        private static final String NEW_ENCODED = "newEncoded";

        @Test
        @DisplayName("Меняет пароль сущности на закодированный новый")
        void updatesEncodedPasswordOnEntity() {
            UUID userId = UUID.randomUUID();
            var request = new ChangePasswordRequest(OLD_RAW, NEW_RAW);
            User user = User.builder().email(EMAIL).password(OLD_ENCODED).build();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(OLD_RAW, OLD_ENCODED)).thenReturn(true);
            when(passwordEncoder.encode(NEW_RAW)).thenReturn(NEW_ENCODED);

            service.changePassword(request, userId);

            assertThat(user.getPassword()).isEqualTo(NEW_ENCODED);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда пользователь не найден")
        void throwsWhenUserNotFound() {
            UUID userId = UUID.randomUUID();
            var request = new ChangePasswordRequest(OLD_RAW, NEW_RAW);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changePassword(request, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
        }

        @Test
        @DisplayName("Бросает BadCredentialsException при неверном старом пароле и не меняет пароль")
        void throwsWhenOldPasswordIsWrong() {
            UUID userId = UUID.randomUUID();
            var request = new ChangePasswordRequest("wrongOld", NEW_RAW);
            User user = User.builder().email(EMAIL).password(OLD_ENCODED).build();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongOld", OLD_ENCODED)).thenReturn(false);

            assertThatThrownBy(() -> service.changePassword(request, userId))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            assertThat(user.getPassword()).isEqualTo(OLD_ENCODED);
        }
    }
}
