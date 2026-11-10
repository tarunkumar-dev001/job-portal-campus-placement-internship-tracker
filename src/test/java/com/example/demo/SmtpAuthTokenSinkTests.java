package com.example.demo;

import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.demo.entity.SecurityToken;
import com.example.demo.service.EmailDeliveryException;
import com.example.demo.service.SmtpAuthTokenSink;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpAuthTokenSinkTests {
    @Test
    void sendsVerificationAndResetLinksWithConfiguredSender() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        SmtpAuthTokenSink sink = new SmtpAuthTokenSink(sender, "no-reply@example.test", "CareerConnect",
                "https://portal.example.test/verify-email?source=mail",
                "https://portal.example.test/reset-password");

        sink.deliver("student@example.test", SecurityToken.Type.EMAIL_VERIFICATION, "raw+token/value");
        assertEquals("Verify your CareerConnect account", message.getSubject());
        assertEquals("no-reply@example.test", ((InternetAddress) message.getFrom()[0]).getAddress());
        assertEquals("CareerConnect", ((InternetAddress) message.getFrom()[0]).getPersonal());
        assertTrue(message.getContent().toString().contains(
                "https://portal.example.test/verify-email?source=mail&token=raw%2Btoken%2Fvalue"),
                message.getContent().toString());

        MimeMessage resetMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(resetMessage);
        sink.deliver("student@example.test", SecurityToken.Type.PASSWORD_RESET, "reset-token");
        assertEquals("Reset your CareerConnect password", resetMessage.getSubject());
        assertTrue(resetMessage.getContent().toString()
                .contains("https://portal.example.test/reset-password?token=reset-token"));
        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void hidesProviderFailureDetailsAndDoesNotReportDeliverySuccess() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("sensitive SMTP server detail"))
                .when(sender).send(any(MimeMessage.class));
        SmtpAuthTokenSink sink = new SmtpAuthTokenSink(sender, "no-reply@example.test", "CareerConnect",
                "https://portal.example.test/verify-email", "https://portal.example.test/reset-password");

        EmailDeliveryException failure = assertThrows(EmailDeliveryException.class,
                () -> sink.deliver("student@example.test", SecurityToken.Type.EMAIL_VERIFICATION, "secret-token"));

        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("sensitive"));
        assertFalse(failure.getMessage().contains("secret-token"));
    }
}
