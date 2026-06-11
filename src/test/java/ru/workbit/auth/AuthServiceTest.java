package ru.workbit.auth;

import org.junit.jupiter.api.BeforeEach;
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
import ru.workbit.auth.service.AuthService;
import ru.workbit.auth.dto.ChangePasswordRequest;
import ru.workbit.auth.dto.LoginRequest;
import ru.workbit.auth.dto.RegistrationRequest;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
    private UserJPARepository userRepository;
    @Mock
    private JWTService jwtService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private AuthService service;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Nested
    @DisplayName("Register")
    class Register {

        private RegistrationRequest request;

        @BeforeEach
        void setUp() {
            request = new RegistrationRequest(EMAIL, RAW_PASSWORD);
        }

        @Test
        @DisplayName("Кодирует пароль, сохраняет пользователя и возвращает токен")
        void shouldEncodePasswordSaveUserAndReturnToken() {
            // given
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateToken(any(User.class))).thenReturn(TOKEN);

            // when
            var response = service.register(request);

            // then
            assertThat(response.accessToken()).isEqualTo(TOKEN);
            assertThat(response.tokenType()).isEqualTo(BEARER);

            verify(userRepository).save(userCaptor.capture());
            var saved = userCaptor.getValue();
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда email уже занят")
        void shouldThrowWhenEmailAlreadyInUse() {
            // given
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            // when / then
            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Email already in use");

            verify(userRepository, never()).save(any());
            verifyNoInteractions(passwordEncoder, jwtService);
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        private User user;

        @BeforeEach
        void setUp() {
            user = User.builder().email(EMAIL).password(ENCODED_PASSWORD).build();
        }

        @Test
        @DisplayName("Возвращает токен при верных учётных данных")
        void shouldReturnTokenForValidCredentials() {
            // given
            var request = new LoginRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtService.generateToken(user)).thenReturn(TOKEN);

            // when
            var response = service.login(request);

            // then
            assertThat(response.accessToken()).isEqualTo(TOKEN);
            assertThat(response.tokenType()).isEqualTo(BEARER);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда пользователь не найден")
        void shouldThrowWhenUserNotFound() {
            // given
            var request = new LoginRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            verifyNoInteractions(passwordEncoder, jwtService);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException при неверном пароле")
        void shouldThrowWhenPasswordIsWrong() {
            // given
            var request = new LoginRequest(EMAIL, "wrongPassword");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPassword", ENCODED_PASSWORD)).thenReturn(false);

            // when / then
            assertThatThrownBy(() -> service.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда пользователь деактивирован")
        void shouldThrowWhenUserIsDeactivated() {
            // given (проверка active стоит ПОСЛЕ verifyPassword, поэтому matches → true)
            var request = new LoginRequest(EMAIL, RAW_PASSWORD);
            var inactiveUser = User.builder().email(EMAIL).password(ENCODED_PASSWORD).active(false).build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(inactiveUser));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            // when / then
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

        private UUID userId;
        private User user;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            user = User.builder().email(EMAIL).password(OLD_ENCODED).build();
        }

        @Test
        @DisplayName("Меняет пароль сущности на закодированный новый")
        void shouldUpdateEncodedPasswordOnEntity() {
            // given
            var request = new ChangePasswordRequest(OLD_RAW, NEW_RAW);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(OLD_RAW, OLD_ENCODED)).thenReturn(true);
            when(passwordEncoder.encode(NEW_RAW)).thenReturn(NEW_ENCODED);

            // when
            service.changePassword(request, userId);

            // then (dirty checking в @Transactional: save не вызывается, проверяем состояние сущности)
            assertThat(user.getPassword()).isEqualTo(NEW_ENCODED);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда пользователь не найден")
        void shouldThrowWhenUserNotFound() {
            // given
            var request = new ChangePasswordRequest(OLD_RAW, NEW_RAW);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.changePassword(request, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");

            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException при неверном старом пароле и не меняет пароль")
        void shouldThrowWhenOldPasswordIsWrong() {
            // given
            var request = new ChangePasswordRequest("wrongOld", NEW_RAW);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongOld", OLD_ENCODED)).thenReturn(false);

            // when / then
            assertThatThrownBy(() -> service.changePassword(request, userId))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            // guard порядка операций: encode не вызывается до проверки, пароль не затёрт
            verify(passwordEncoder, never()).encode(any());
            assertThat(user.getPassword()).isEqualTo(OLD_ENCODED);
        }
    }
}
