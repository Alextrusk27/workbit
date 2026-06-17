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
import ru.workbit.email.ResetPasswordEmailEvent;
import ru.workbit.email.VerificationEmailEvent;
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
    public void onVerificationEmailRequested(VerificationEmailEvent event) {
        try {
            sendVerificationMail(event.email(), event.token());
        } catch (EmailSendException ignored) {
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResetPasswordEmailRequested(ResetPasswordEmailEvent event) {
        try {
            sendResetPasswordMail(event.email(), event.token());
        } catch (EmailSendException ignored) {
        }
    }

    public void sendVerificationMail(String to, String token) {
        Context context = new Context();
        context.setVariable("verificationUrl", createVerificationLink(token));

        String html = templateEngine.process("email/verification", context);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(mailProperties.fromMail());
            helper.setTo(to);
            helper.setSubject("Подтверждение электронной почты");
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("Verification email sent");
        } catch (MessagingException e) {
            log.error("Failed to send verification email", e);
            throw new EmailSendException("Failed to send verification email", e);
        }
    }

    public void sendResetPasswordMail(String to, String token) {
        Context context = new Context();
        context.setVariable("resetUrl", createResetPasswordLink(token));

        String html = templateEngine.process("email/reset-password", context);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(mailProperties.fromMail());
            helper.setTo(to);
            helper.setSubject("Изменение пароля для аккаунта");
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("Reset password email sent");
        } catch (MessagingException e) {
            log.error("Failed to send reset password email", e);
            throw new EmailSendException("Failed to send reset password email", e);
        }
    }

    private String createVerificationLink(String token) {
        return mailProperties.baseUrl() + "/api/v1/auth/verify-email?token=" + token;
    }

    private String createResetPasswordLink(String token) {
        return mailProperties.baseUrl() + "/api/v1/auth/reset-password?token=" + token;
    }
}
