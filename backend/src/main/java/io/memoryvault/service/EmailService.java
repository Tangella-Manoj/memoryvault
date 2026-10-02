package io.memoryvault.service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for sending transactional outbound emails (e.g. password reset verification codes).
 *
 * <p>Supports multiple email delivery providers with graceful fallback:
 * <ul>
 *   <li>Resend REST API (via {@code RESEND_API_KEY})</li>
 *   <li>Standard SMTP (Brevo, Gmail, SendGrid, Mailjet, AWS SES via {@code SMTP_HOST})</li>
 *   <li>Mailgun REST API (via {@code MAILGUN_API_KEY} &amp; {@code MAILGUN_DOMAIN})</li>
 * </ul>
 *
 * <p>All interactive delivery endpoints support non-blocking asynchronous dispatch
 * via {@link #sendPasswordResetEmailAsync(String, String)} to eliminate network latency
 * on HTTP request threads.
 */
public interface EmailService {

    /**
     * Checks if at least one outbound email delivery provider is configured.
     *
     * @return true if credentials for Resend, SMTP, or Mailgun are configured
     */
    boolean isConfigured();

    /**
     * Asynchronously sends a 6-digit password reset verification code.
     *
     * @param toEmail   recipient email address
     * @param resetCode 6-digit verification OTP
     * @return CompletableFuture completing with true if sent, false otherwise
     */
    CompletableFuture<Boolean> sendPasswordResetEmailAsync(String toEmail, String resetCode);

    /**
     * Synchronously sends a 6-digit password reset verification code.
     *
     * @param toEmail   recipient email address
     * @param resetCode 6-digit verification OTP
     * @return true if successfully delivered via any configured provider, false otherwise
     */
    boolean sendPasswordResetEmail(String toEmail, String resetCode);

    /**
     * Sends an HTML email with an optional plain text fallback.
     *
     * @param toEmail      recipient email address
     * @param subject      email subject line
     * @param htmlContent  HTML formatted email body
     * @param plainContent plain text alternative
     * @return true if successfully sent, false otherwise
     */
    boolean sendHtmlEmail(String toEmail, String subject, String htmlContent, String plainContent);
}
