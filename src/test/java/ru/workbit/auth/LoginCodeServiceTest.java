package ru.workbit.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.auth.model.LoginCode;
import ru.workbit.auth.model.User;
import ru.workbit.auth.repository.LoginCodeJPARepository;
import ru.workbit.auth.service.LoginCodeService;
import ru.workbit.auth.service.TokenHasher;
import ru.workbit.exception.BadCredentialsException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginCodeServiceTest")
class LoginCodeServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "user@example.com";
    private static final String RAW_CODE = "654321";
    private static final String MATCHING_HASH = "matching-hash";

    @Mock
    LoginCodeJPARepository loginCodeRepository;
    @Mock
    TokenHasher tokenHasher;

    @InjectMocks
    LoginCodeService loginCodeService;

    private User user() {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .build();
    }

    private LoginCode activeCode(User user, String codeHash) {
        return LoginCode.builder()
                .user(user)
                .codeHash(codeHash)
                .expiresAt(Instant.now().plusSeconds(900))
                .attempts(0)
                .build();
    }

    // -------------------------------------------------------------------------
    // issue
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Issue")
    class Issue {

        @Test
        @DisplayName("Гасит активные коды пользователя перед выпуском нового")
        void invalidatesActiveCodesBeforeIssuingNew() {
            // given
            var user = user();
            var active1 = activeCode(user, "h1");
            var active2 = activeCode(user, "h2");
            when(loginCodeRepository.findAllByUserAndUsedAtIsNull(user)).thenReturn(List.of(active1, active2));
            when(tokenHasher.hash(anyString())).thenReturn(MATCHING_HASH);
            when(loginCodeRepository.save(any(LoginCode.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            loginCodeService.issue(user);

            // then
            assertThat(active1.getUsedAt()).isNotNull().isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
            assertThat(active2.getUsedAt()).isNotNull().isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("Выпускает шестизначный код и сохраняет его хэш с солью userId")
        void issuesSixDigitCodeAndSavesHashedCode() {
            // given
            var user = user();
            when(loginCodeRepository.findAllByUserAndUsedAtIsNull(user)).thenReturn(List.of());
            when(tokenHasher.hash(anyString())).thenReturn(MATCHING_HASH);
            when(loginCodeRepository.save(any(LoginCode.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var rawCode = loginCodeService.issue(user);

            // then
            assertThat(rawCode).matches("\\d{6}");

            var hashInputCaptor = ArgumentCaptor.forClass(String.class);
            verify(tokenHasher).hash(hashInputCaptor.capture());
            assertThat(hashInputCaptor.getValue()).isEqualTo(USER_ID + ":" + rawCode);

            var codeCaptor = ArgumentCaptor.forClass(LoginCode.class);
            verify(loginCodeRepository).save(codeCaptor.capture());
            assertThat(codeCaptor.getValue().getUser()).isEqualTo(user);
            assertThat(codeCaptor.getValue().getCodeHash()).isEqualTo(MATCHING_HASH);
        }
    }

    // -------------------------------------------------------------------------
    // consume
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Consume")
    class Consume {

        @Test
        @DisplayName("Помечает код использованным при совпадении хэша (обеспечивает одноразовость)")
        void marksCodeUsedOnMatchingHash() {
            // given
            var user = user();
            var code = activeCode(user, MATCHING_HASH);
            when(loginCodeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user))
                    .thenReturn(Optional.of(code));
            when(tokenHasher.hash(USER_ID + ":" + RAW_CODE)).thenReturn(MATCHING_HASH);

            // when
            loginCodeService.consume(user, RAW_CODE);

            // then
            assertThat(code.getUsedAt()).isNotNull().isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("Бросает BadCredentialsException и инкрементирует attempts при несовпадении хэша")
        void throwsAndIncrementsAttemptsOnHashMismatch() {
            // given
            var user = user();
            var code = activeCode(user, "different-hash");
            code.setAttempts(2);
            when(loginCodeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user))
                    .thenReturn(Optional.of(code));
            when(tokenHasher.hash(USER_ID + ":" + RAW_CODE)).thenReturn(MATCHING_HASH);

            // when / then
            assertThatThrownBy(() -> loginCodeService.consume(user, RAW_CODE))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid code");

            assertThat(code.getAttempts()).isEqualTo(3);
            assertThat(code.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда код истёк")
        void throwsWhenCodeExpired() {
            // given
            var user = user();
            var code = activeCode(user, MATCHING_HASH);
            code.setExpiresAt(Instant.now().minus(Duration.ofSeconds(1)));
            when(loginCodeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user))
                    .thenReturn(Optional.of(code));

            // when / then
            assertThatThrownBy(() -> loginCodeService.consume(user, RAW_CODE))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Code has expired");

            verifyNoInteractions(tokenHasher);
            assertThat(code.getAttempts()).isZero();
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда попытки исчерпаны")
        void throwsWhenAttemptsExhausted() {
            // given
            var user = user();
            var code = activeCode(user, MATCHING_HASH);
            code.setAttempts(5);
            when(loginCodeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user))
                    .thenReturn(Optional.of(code));

            // when / then
            assertThatThrownBy(() -> loginCodeService.consume(user, RAW_CODE))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Too many attempts");

            verifyNoInteractions(tokenHasher);
        }

        @Test
        @DisplayName("Бросает BadCredentialsException, когда активного кода нет")
        void throwsWhenNoActiveCodeFound() {
            // given
            var user = user();
            when(loginCodeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user))
                    .thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> loginCodeService.consume(user, RAW_CODE))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid code");

            verifyNoInteractions(tokenHasher);
        }
    }
}
