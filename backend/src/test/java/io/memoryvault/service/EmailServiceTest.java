package io.memoryvault.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailServiceTest {

    @Test
    @DisplayName("isConfigured returns false when no provider credentials are provided")
    void isConfigured_noneConfigured_returnsFalse() {
        DefaultEmailService service = new DefaultEmailService(
                "MemoryVault <noreply@vault.dev>", "MemoryVault",
                "", "", "", "", 587, "", "", true, true, false
        );

        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured returns true when RESEND_API_KEY is provided")
    void isConfigured_resendConfigured_returnsTrue() {
        DefaultEmailService service = new DefaultEmailService(
                "MemoryVault <noreply@vault.dev>", "MemoryVault",
                "re_test_12345", "", "", "", 587, "", "", true, true, false
        );

        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("isConfigured returns true when SMTP_HOST is provided")
    void isConfigured_smtpConfigured_returnsTrue() {
        DefaultEmailService service = new DefaultEmailService(
                "MemoryVault <noreply@vault.dev>", "MemoryVault",
                "", "", "", "smtp-relay.brevo.com", 587, "user", "pass", true, true, false
        );

        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("isConfigured returns true when MAILGUN credentials are provided")
    void isConfigured_mailgunConfigured_returnsTrue() {
        DefaultEmailService service = new DefaultEmailService(
                "MemoryVault <noreply@vault.dev>", "MemoryVault",
                "", "key-12345", "mg.example.com", "", 587, "", "", true, true, false
        );

        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("sendPasswordResetEmail rejects null or blank email")
    void sendPasswordResetEmail_invalidEmail_returnsFalse() {
        DefaultEmailService service = new DefaultEmailService(
                "MemoryVault <noreply@vault.dev>", "MemoryVault",
                "re_mock", "", "", "", 587, "", "", true, true, false
        );

        assertThat(service.sendPasswordResetEmail(null, "123456")).isFalse();
        assertThat(service.sendPasswordResetEmail("   ", "123456")).isFalse();
        assertThat(service.sendPasswordResetEmail("invalid-no-at-sign", "123456")).isFalse();
    }

    @Test
    @DisplayName("sendPasswordResetEmail returns false gracefully when no provider is configured")
    void sendPasswordResetEmail_unconfigured_returnsFalseGracefully() {
        DefaultEmailService service = new DefaultEmailService(
                "MemoryVault <noreply@vault.dev>", "MemoryVault",
                "", "", "", "", 587, "", "", true, true, false
        );

        boolean result = service.sendPasswordResetEmail("user@example.com", "123456");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("NoOpEmailService returns false and completes future safely")
    void noOpEmailService_behavior() throws Exception {
        NoOpEmailService noop = new NoOpEmailService();
        assertThat(noop.isConfigured()).isFalse();
        assertThat(noop.sendPasswordResetEmail("user@example.com", "123456")).isFalse();
        assertThat(noop.sendHtmlEmail("user@example.com", "Subject", "<p>hi</p>", "hi")).isFalse();
        assertThat(noop.sendPasswordResetEmailAsync("user@example.com", "123456").get()).isFalse();
    }
}
