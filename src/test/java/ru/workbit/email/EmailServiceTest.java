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
    private static final String TOKEN = "abc123token";
    private static final String BASE_URL = "https://workbit.ru";
    private static final String FROM_MAIL = "noreply@workbit.ru";

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
    @DisplayName("SendVerificationMail")
    class SendVerificationMail {

        @Test
        @DisplayName("Вызывает правильный шаблон и send при успешной отправке")
        void callsCorrectTemplateAndSend() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/verification"), any(Context.class)))
                    .thenReturn("<html>verify</html>");

            // when
            service.sendVerificationMail(TO, TOKEN);

            // then
            verify(templateEngine).process(eq("email/verification"), any(Context.class));
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Передаёт корректный verificationUrl в контекст шаблона")
        void setsCorrectVerificationUrl() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/verification"), contextCaptor.capture()))
                    .thenReturn("<html>verify</html>");

            // when
            service.sendVerificationMail(TO, TOKEN);

            // then
            var capturedContext = contextCaptor.getValue();
            var expectedUrl = BASE_URL + "/api/v1/auth/verify-email?token=" + TOKEN;
            assertThat(capturedContext.getVariable("verificationUrl")).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("Бросает EmailSendException, когда MimeMessage#setSubject кидает MessagingException")
        void throwsEmailSendExceptionOnMessagingException() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(templateEngine.process(eq("email/verification"), any(Context.class)))
                    .thenReturn("<html>verify</html>");

            MimeMessage spyMessage = spy(realMimeMessage());
            when(mailSender.createMimeMessage()).thenReturn(spyMessage);

            try {
                doThrow(new MessagingException("smtp error"))
                        .when(spyMessage).setSubject(any(), any());
            } catch (MessagingException ignored) {
            }

            // when / then
            assertThatThrownBy(() -> service.sendVerificationMail(TO, TOKEN))
                    .isInstanceOf(EmailSendException.class)
                    .hasMessage("Failed to send verification email");
        }
    }

    @Nested
    @DisplayName("SendResetPasswordMail")
    class SendResetPasswordMail {

        @Test
        @DisplayName("Вызывает правильный шаблон и send при успешной отправке")
        void callsCorrectTemplateAndSend() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/reset-password"), any(Context.class)))
                    .thenReturn("<html>reset</html>");

            // when
            service.sendResetPasswordMail(TO, TOKEN);

            // then
            verify(templateEngine).process(eq("email/reset-password"), any(Context.class));
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Передаёт корректный resetUrl в контекст шаблона")
        void setsCorrectResetUrl() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/reset-password"), contextCaptor.capture()))
                    .thenReturn("<html>reset</html>");

            // when
            service.sendResetPasswordMail(TO, TOKEN);

            // then
            var capturedContext = contextCaptor.getValue();
            var expectedUrl = BASE_URL + "/api/v1/auth/reset-password?token=" + TOKEN;
            assertThat(capturedContext.getVariable("resetUrl")).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("Бросает EmailSendException, когда MimeMessage#setSubject кидает MessagingException")
        void throwsEmailSendExceptionOnMessagingException() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(templateEngine.process(eq("email/reset-password"), any(Context.class)))
                    .thenReturn("<html>reset</html>");

            MimeMessage spyMessage = spy(realMimeMessage());
            when(mailSender.createMimeMessage()).thenReturn(spyMessage);
            try {
                doThrow(new MessagingException("smtp error"))
                        .when(spyMessage).setSubject(any(), any());
            } catch (MessagingException ignored) {
            }

            // when / then
            assertThatThrownBy(() -> service.sendResetPasswordMail(TO, TOKEN))
                    .isInstanceOf(EmailSendException.class)
                    .hasMessage("Failed to send reset password email");
        }
    }

    @Nested
    @DisplayName("OnVerificationEmailRequested")
    class OnVerificationEmailRequested {

        @Test
        @DisplayName("Вызывает sendVerificationMail с данными из события")
        void delegatesToSendVerificationMail() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/verification"), any(Context.class)))
                    .thenReturn("<html>verify</html>");

            var event = new VerificationEmailEvent(TO, TOKEN);

            // when
            service.onVerificationEmailRequested(event);

            // then
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Проглатывает EmailSendException и не пробрасывает её")
        void swallowsEmailSendException() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(templateEngine.process(eq("email/verification"), any(Context.class)))
                    .thenReturn("<html>verify</html>");

            MimeMessage spyMessage = spy(realMimeMessage());
            when(mailSender.createMimeMessage()).thenReturn(spyMessage);
            try {
                doThrow(new MessagingException("smtp error"))
                        .when(spyMessage).setSubject(any(), any());
            } catch (MessagingException ignored) {
            }

            var event = new VerificationEmailEvent(TO, TOKEN);

            // when / then
            assertThatCode(() -> service.onVerificationEmailRequested(event))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("OnResetPasswordEmailRequested")
    class OnResetPasswordEmailRequested {

        @Test
        @DisplayName("Вызывает sendResetPasswordMail с данными из события")
        void delegatesToSendResetPasswordMail() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
            when(templateEngine.process(eq("email/reset-password"), any(Context.class)))
                    .thenReturn("<html>reset</html>");

            var event = new ResetPasswordEmailEvent(TO, TOKEN);

            // when
            service.onResetPasswordEmailRequested(event);

            // then
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Проглатывает EmailSendException и не пробрасывает её")
        void swallowsEmailSendException() {
            // given
            when(mailProperties.baseUrl()).thenReturn(BASE_URL);
            when(mailProperties.fromMail()).thenReturn(FROM_MAIL);
            when(templateEngine.process(eq("email/reset-password"), any(Context.class)))
                    .thenReturn("<html>reset</html>");

            MimeMessage spyMessage = spy(realMimeMessage());
            when(mailSender.createMimeMessage()).thenReturn(spyMessage);
            try {
                doThrow(new MessagingException("smtp error"))
                        .when(spyMessage).setSubject(any(), any());
            } catch (MessagingException ignored) {
            }

            var event = new ResetPasswordEmailEvent(TO, TOKEN);

            // when / then
            assertThatCode(() -> service.onResetPasswordEmailRequested(event))
                    .doesNotThrowAnyException();
        }
    }
}
