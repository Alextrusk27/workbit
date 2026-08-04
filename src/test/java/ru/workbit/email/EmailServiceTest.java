package ru.workbit.email;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import ru.workbit.email.properties.MailProperties;
import ru.workbit.email.service.EmailService;
import ru.workbit.exception.EmailSendException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceTest")
class EmailServiceTest {

    private static final String TO = "user@workbit.ru";
    private static final String CODE = "123456";
    private static final String FROM_MAIL = "noreply@workbit.ru";
    private static final String BASE_URL = "https://workbit.ru";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MailProperties mailProperties;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService service;

    @Captor
    private ArgumentCaptor<Context> contextCaptor;

    private MimeMessage realMimeMessage() {
        return new MimeMessage((Session) null);
    }

    @Nested
    @DisplayName("SendLoginCodeMail")
    class SendLoginCodeMail {

        @Test
        @DisplayName("Вызывает правильный шаблон и send при успешной отправке")
        void callsCorrectTemplateAndSend() {
            // given
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/login-code"), any(Context.class)))
                    .thenReturn("<html>code</html>");

            // when
            service.sendLoginCodeMail(TO, CODE);

            // then
            verify(templateEngine).process(eq("email/login-code"), any(Context.class));
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Передаёт код в контекст шаблона")
        void setsCodeInTemplateContext() {
            // given
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/login-code"), contextCaptor.capture()))
                    .thenReturn("<html>code</html>");

            // when
            service.sendLoginCodeMail(TO, CODE);

            // then
            var capturedContext = contextCaptor.getValue();
            assertThat(capturedContext.getVariable("code")).isEqualTo(CODE);
        }

        @Test
        @DisplayName("Бросает EmailSendException, когда MimeMessage#setSubject кидает MessagingException")
        void throwsEmailSendExceptionOnMessagingException() {
            // given
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(templateEngine.process(eq("email/login-code"), any(Context.class)))
                    .thenReturn("<html>code</html>");

            MimeMessage spyMessage = spy(realMimeMessage());
            when(mailSender.createMimeMessage()).thenReturn(spyMessage);

            try {
                doThrow(new MessagingException("smtp error"))
                        .when(spyMessage).setSubject(any(), any());
            } catch (MessagingException ignored) {
            }

            // when / then
            assertThatThrownBy(() -> service.sendLoginCodeMail(TO, CODE))
                    .isInstanceOf(EmailSendException.class)
                    .hasMessage("Failed to send login code email");
        }
    }

    @Nested
    @DisplayName("SendAccountDeletionWarningMail")
    class SendAccountDeletionWarningMail {

        @Test
        @DisplayName("Вызывает правильный шаблон и send при успешной отправке")
        void callsCorrectTemplateAndSend() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/account-deletion-warning"), any(Context.class)))
                    .thenReturn("<html>warning</html>");

            // when
            service.sendAccountDeletionWarningMail(TO);

            // then
            verify(templateEngine).process(eq("email/account-deletion-warning"), any(Context.class));
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Бросает EmailSendException, когда MimeMessage#setSubject кидает MessagingException")
        void throwsEmailSendExceptionOnMessagingException() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(templateEngine.process(eq("email/account-deletion-warning"), any(Context.class)))
                    .thenReturn("<html>warning</html>");

            MimeMessage spyMessage = spy(realMimeMessage());
            when(mailSender.createMimeMessage()).thenReturn(spyMessage);
            try {
                doThrow(new MessagingException("smtp error"))
                        .when(spyMessage).setSubject(any(), any());
            } catch (MessagingException ignored) {
            }

            // when / then
            assertThatThrownBy(() -> service.sendAccountDeletionWarningMail(TO))
                    .isInstanceOf(EmailSendException.class)
                    .hasMessage("Failed to send account deletion warning email");
        }
    }

    @Nested
    @DisplayName("OnLoginCodeEmailRequested")
    class OnLoginCodeEmailRequested {

        @Test
        @DisplayName("Вызывает sendLoginCodeMail с данными из события")
        void delegatesToSendLoginCodeMail() {
            // given
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/login-code"), any(Context.class)))
                    .thenReturn("<html>code</html>");

            var event = new LoginCodeEmailEvent(TO, CODE);

            // when
            service.onLoginCodeEmailRequested(event);

            // then
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Проглатывает EmailSendException и не пробрасывает её")
        void swallowsEmailSendException() {
            // given
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(templateEngine.process(eq("email/login-code"), any(Context.class)))
                    .thenReturn("<html>code</html>");

            MimeMessage spyMessage = spy(realMimeMessage());
            when(mailSender.createMimeMessage()).thenReturn(spyMessage);
            try {
                doThrow(new MessagingException("smtp error"))
                        .when(spyMessage).setSubject(any(), any());
            } catch (MessagingException ignored) {
            }

            var event = new LoginCodeEmailEvent(TO, CODE);

            // when / then
            assertThatCode(() -> service.onLoginCodeEmailRequested(event))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("OnAccountDeletionWarningRequested")
    class OnAccountDeletionWarningRequested {

        @Test
        @DisplayName("Вызывает sendAccountDeletionWarningMail с данными из события")
        void delegatesToSendAccountDeletionWarningMail() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/account-deletion-warning"), any(Context.class)))
                    .thenReturn("<html>warning</html>");

            var event = new AccountDeletionWarningEmailEvent(TO);

            // when
            service.onAccountDeletionWarningRequested(event);

            // then
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Проглатывает EmailSendException и не пробрасывает её")
        void swallowsEmailSendException() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(templateEngine.process(eq("email/account-deletion-warning"), any(Context.class)))
                    .thenReturn("<html>warning</html>");

            MimeMessage spyMessage = spy(realMimeMessage());
            when(mailSender.createMimeMessage()).thenReturn(spyMessage);
            try {
                doThrow(new MessagingException("smtp error"))
                        .when(spyMessage).setSubject(any(), any());
            } catch (MessagingException ignored) {
            }

            var event = new AccountDeletionWarningEmailEvent(TO);

            // when / then
            assertThatCode(() -> service.onAccountDeletionWarningRequested(event))
                    .doesNotThrowAnyException();
        }
    }
}
