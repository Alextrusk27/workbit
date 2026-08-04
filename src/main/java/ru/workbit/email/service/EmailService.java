package ru.workbit.email.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import ru.workbit.email.AccountDeletionWarningEmailEvent;
import ru.workbit.email.LoginCodeEmailEvent;
import ru.workbit.email.properties.MailProperties;
import ru.workbit.exception.EmailSendException;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final TemplateEngine templateEngine;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoginCodeEmailRequested(LoginCodeEmailEvent event) {
        try {
            sendLoginCodeMail(event.email(), event.code());
        } catch (EmailSendException ignored) {
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountDeletionWarningRequested(AccountDeletionWarningEmailEvent event) {
        try {
            sendAccountDeletionWarningMail(event.email());
        } catch (EmailSendException ignored) {
        }
    }

    public void sendLoginCodeMail(String to, String code) {
        Context context = new Context();
        context.setVariable("code", code);

        String html = templateEngine.process("email/login-code", context);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(mailProperties.fromMail());
            helper.setTo(to);
            helper.setSubject("Код для входа в Workbit");
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("Login code email sent");
        } catch (MessagingException e) {
            log.error("Failed to send login code email", e);
            throw new EmailSendException("Failed to send login code email", e);
        }
    }

    public void sendAccountDeletionWarningMail(String to) {
        Context context = new Context();
        context.setVariable("loginUrl", mailProperties.baseUrl() + "/login");

        String html = templateEngine.process("email/account-deletion-warning", context);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(mailProperties.fromMail());
            helper.setTo(to);
            helper.setSubject("Ваш аккаунт Workbit скоро будет удалён");
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("Account deletion warning email sent");
        } catch (MessagingException e) {
            log.error("Failed to send account deletion warning email", e);
            throw new EmailSendException("Failed to send account deletion warning email", e);
        }
    }
}
