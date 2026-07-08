package ru.workbit.email;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import ru.workbit.email.properties.MailProperties;
import ru.workbit.email.service.EmailService;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailServiceGreenMailTest")
class EmailServiceGreenMailTest {

    private static final String TO = "user@workbit.ru";
    private static final String TOKEN = "abc123token";
    private static final String BASE_URL = "https://workbit.ru";
    private static final String FROM_MAIL = "noreply@workbit.ru";
    private static final String FROM_NAME = "Workbit";
    private static final int RESET_TTL_MINUTES = 15;

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private EmailService service;

    @BeforeEach
    void setUp() {
        var mailSender = new JavaMailSenderImpl();
        mailSender.setHost("127.0.0.1");
        mailSender.setPort(greenMail.getSmtp().getPort());

        var mailProperties = new MailProperties(FROM_NAME, FROM_MAIL, BASE_URL, RESET_TTL_MINUTES);

        service = new EmailService(mailSender, mailProperties, templateEngine());
    }

    private TemplateEngine templateEngine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private MimeMessage singleReceivedMessage() {
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        var messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        return messages[0];
    }

    private String htmlBody(MimeMessage message) throws Exception {
        return extractHtml(message.getContent());
    }

    private String extractHtml(Object content) throws Exception {
        if (content instanceof MimeMultipart multipart) {
            for (var i = 0; i < multipart.getCount(); i++) {
                var part = multipart.getBodyPart(i);
                if (part.isMimeType("text/html")) {
                    return (String) part.getContent();
                }
                if (part.getContent() instanceof MimeMultipart) {
                    return extractHtml(part.getContent());
                }
            }
        }
        throw new IllegalStateException("HTML-часть не найдена");
    }

    @Nested
    @DisplayName("SendVerificationMail")
    class SendVerificationMail {

        private static final String SUBJECT = "Подтверждение электронной почты";
        private static final String EXPECTED_URL = BASE_URL + "/verify-email?token=" + TOKEN;

        @Test
        @DisplayName("Доставляет письмо с корректными заголовками на SMTP-сервер")
        void deliversMailWithCorrectHeaders() throws Exception {
            // when
            service.sendVerificationMail(TO, TOKEN);

            // then
            var message = singleReceivedMessage();
            assertThat(message.getSubject()).isEqualTo(SUBJECT);
            assertThat(message.getFrom()[0].toString()).isEqualTo(FROM_MAIL);
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
        }

        @Test
        @DisplayName("Подставляет verificationUrl в HTML-тело письма")
        void rendersVerificationUrlInBody() throws Exception {
            // when
            service.sendVerificationMail(TO, TOKEN);

            // then
            var body = htmlBody(singleReceivedMessage());
            assertThat(body).contains(EXPECTED_URL);
        }
    }

    @Nested
    @DisplayName("SendResetPasswordMail")
    class SendResetPasswordMail {

        private static final String SUBJECT = "Изменение пароля для аккаунта";
        private static final String EXPECTED_URL = BASE_URL + "/reset-password?token=" + TOKEN;

        @Test
        @DisplayName("Доставляет письмо с корректными заголовками на SMTP-сервер")
        void deliversMailWithCorrectHeaders() throws Exception {
            // when
            service.sendResetPasswordMail(TO, TOKEN);

            // then
            var message = singleReceivedMessage();
            assertThat(message.getSubject()).isEqualTo(SUBJECT);
            assertThat(message.getFrom()[0].toString()).isEqualTo(FROM_MAIL);
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
        }

        @Test
        @DisplayName("Подставляет resetUrl в HTML-тело письма")
        void rendersResetUrlInBody() throws Exception {
            // when
            service.sendResetPasswordMail(TO, TOKEN);

            // then
            var body = htmlBody(singleReceivedMessage());
            assertThat(body).contains(EXPECTED_URL);
        }
    }

    @Nested
    @DisplayName("OnVerificationEmailRequested")
    class OnVerificationEmailRequested {

        @Test
        @DisplayName("Доставляет письмо по данным из события")
        void deliversMailFromEvent() throws Exception {
            // when
            service.onVerificationEmailRequested(new VerificationEmailEvent(TO, TOKEN));

            // then
            var message = singleReceivedMessage();
            assertThat(message.getSubject()).isEqualTo("Подтверждение электронной почты");
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
        }
    }

    @Nested
    @DisplayName("OnResetPasswordEmailRequested")
    class OnResetPasswordEmailRequested {

        @Test
        @DisplayName("Доставляет письмо по данным из события")
        void deliversMailFromEvent() throws Exception {
            // when
            service.onResetPasswordEmailRequested(new ResetPasswordEmailEvent(TO, TOKEN));

            // then
            var message = singleReceivedMessage();
            assertThat(message.getSubject()).isEqualTo("Изменение пароля для аккаунта");
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
        }
    }
}
