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
    private static final String CODE = "123456";
    private static final String BASE_URL = "https://workbit.ru";
    private static final String FROM_MAIL = "noreply@workbit.ru";
    private static final String FROM_NAME = "Workbit";

    @RegisterExtension
    GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private EmailService service;

    @BeforeEach
    void setUp() {
        var mailSender = new JavaMailSenderImpl();
        mailSender.setHost("127.0.0.1");
        mailSender.setPort(greenMail.getSmtp().getPort());

        var mailProperties = new MailProperties(FROM_NAME, FROM_MAIL, BASE_URL);

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
    @DisplayName("SendLoginCodeMail")
    class SendLoginCodeMail {

        private static final String SUBJECT = "Код для входа в Workbit";

        @Test
        @DisplayName("Доставляет письмо с корректными заголовками на SMTP-сервер")
        void deliversMailWithCorrectHeaders() throws Exception {
            // when
            service.sendLoginCodeMail(TO, CODE);

            // then
            var message = singleReceivedMessage();
            assertThat(message.getSubject()).isEqualTo(SUBJECT);
            assertThat(message.getFrom()[0].toString()).isEqualTo(FROM_MAIL);
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
        }

        @Test
        @DisplayName("Подставляет код в HTML-тело письма")
        void rendersCodeInBody() throws Exception {
            // when
            service.sendLoginCodeMail(TO, CODE);

            // then
            var body = htmlBody(singleReceivedMessage());
            assertThat(body).contains(CODE);
        }
    }

    @Nested
    @DisplayName("SendAccountDeletionWarningMail")
    class SendAccountDeletionWarningMail {

        private static final String SUBJECT = "Ваш аккаунт Workbit скоро будет удалён";
        private static final String EXPECTED_URL = BASE_URL + "/login";

        @Test
        @DisplayName("Доставляет письмо с корректными заголовками на SMTP-сервер")
        void deliversMailWithCorrectHeaders() throws Exception {
            // when
            service.sendAccountDeletionWarningMail(TO);

            // then
            var message = singleReceivedMessage();
            assertThat(message.getSubject()).isEqualTo(SUBJECT);
            assertThat(message.getFrom()[0].toString()).isEqualTo(FROM_MAIL);
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
        }

        @Test
        @DisplayName("Подставляет loginUrl и текст предупреждения в HTML-тело письма")
        void rendersLoginUrlAndWarningInBody() throws Exception {
            // when
            service.sendAccountDeletionWarningMail(TO);

            // then
            var body = htmlBody(singleReceivedMessage());
            assertThat(body).contains(EXPECTED_URL);
            assertThat(body).contains("30 дней");
        }
    }

    @Nested
    @DisplayName("OnLoginCodeEmailRequested")
    class OnLoginCodeEmailRequested {

        @Test
        @DisplayName("Доставляет письмо по данным из события")
        void deliversMailFromEvent() throws Exception {
            // when
            service.onLoginCodeEmailRequested(new LoginCodeEmailEvent(TO, CODE));

            // then
            var message = singleReceivedMessage();
            assertThat(message.getSubject()).isEqualTo("Код для входа в Workbit");
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
            assertThat(htmlBody(message)).contains(CODE);
        }
    }

    @Nested
    @DisplayName("OnAccountDeletionWarningRequested")
    class OnAccountDeletionWarningRequested {

        @Test
        @DisplayName("Доставляет письмо по данным из события")
        void deliversMailFromEvent() throws Exception {
            // when
            service.onAccountDeletionWarningRequested(new AccountDeletionWarningEmailEvent(TO));

            // then
            var message = singleReceivedMessage();
            assertThat(message.getSubject()).isEqualTo("Ваш аккаунт Workbit скоро будет удалён");
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
        }
    }
}
