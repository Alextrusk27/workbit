package ru.workbit.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import ru.workbit.email.ResetPasswordEmailEvent;
import ru.workbit.email.VerificationEmailEvent;

import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;

/**
 * Тест-конфигурация для e2e-тестов auth-домена:
 * - перехват токенов из ApplicationEvent без реального SMTP
 * - mock-заглушка JavaMailSender
 */
@TestConfiguration
public class AuthTestConfig {

    @Bean
    public TokenCaptor tokenCaptor() {
        return new TokenCaptor();
    }

    /**
     * Заглушка вместо реального JavaMailSender.
     * EmailService не упадёт при отсутствии SMTP-сервера.
     */
    @Bean
    @Primary
    public JavaMailSender mockMailSender() {
        return mock(JavaMailSender.class);
    }

    /**
     * Перехватывает токены из ApplicationEvent без отправки реальных писем.
     * Использует синхронный @EventListener (не @TransactionalEventListener),
     * поэтому вызывается в момент публикации события — внутри транзакции AuthService.
     * К моменту возврата HTTP-ответа токен уже доступен.
     */
    public static class TokenCaptor {
        private final ConcurrentHashMap<String, String> verificationTokens = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> resetTokens = new ConcurrentHashMap<>();

        @EventListener
        public void onVerification(VerificationEmailEvent event) {
            verificationTokens.put(event.email(), event.token());
        }

        @EventListener
        public void onReset(ResetPasswordEmailEvent event) {
            resetTokens.put(event.email(), event.token());
        }

        public String getVerificationToken(String email) {
            return verificationTokens.get(email);
        }

        public String getResetToken(String email) {
            return resetTokens.get(email);
        }
    }
}
