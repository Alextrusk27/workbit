package ru.workbit.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import ru.workbit.email.LoginCodeEmailEvent;

import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;

/**
 * Тест-конфигурация для e2e-тестов auth-домена:
 * - перехват кода входа из ApplicationEvent без реального SMTP
 * - mock-заглушка JavaMailSender
 */
@TestConfiguration
public class AuthTestConfig {

    @Bean
    public CodeCaptor codeCaptor() {
        return new CodeCaptor();
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
     * Перехватывает код входа из ApplicationEvent без отправки реальных писем.
     * Использует синхронный @EventListener (не @TransactionalEventListener),
     * поэтому вызывается в момент публикации события — внутри транзакции AuthService.
     * К моменту возврата HTTP-ответа код уже доступен.
     */
    public static class CodeCaptor {
        private final ConcurrentHashMap<String, String> codes = new ConcurrentHashMap<>();

        @EventListener
        public void onLoginCode(LoginCodeEmailEvent event) {
            codes.put(event.email(), event.code());
        }

        public String getCode(String email) {
            return codes.get(email);
        }
    }
}
