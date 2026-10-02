package io.memoryvault.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Production implementation of {@link EmailService}.
 *
 * <p>Supports sending transactional emails via:
 * <ul>
 *   <li><strong>Resend REST API</strong>: Free tier 3,000/mo, zero credit card, instant API dispatch.</li>
 *   <li><strong>Standard SMTP</strong>: Brevo (300/day free), Gmail SMTP, Mailjet, AWS SES.</li>
 *   <li><strong>Mailgun REST API</strong>: Inbound &amp; outbound integration.</li>
 * </ul>
 *
 * <p>Provides multi-provider fallback: if Resend is configured but fails, it attempts SMTP if available.
 * If sending fails or no provider is configured, it logs full diagnostics and safely reports failure
 * so the auth layer can provide emergency fallback codes without locking out users.
 */
@Service
public class DefaultEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailService.class);

    private final String fromAddress;
    private final String fromName;
    private final String resendApiKey;
    private final String mailgunApiKey;
    private final String mailgunDomain;
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final boolean smtpAuth;
    private final boolean smtpStarttls;
    private final boolean smtpSsl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DefaultEmailService(
            @Value("${app.mail.from:MemoryVault <onboarding@resend.dev>}") String fromAddress,
            @Value("${app.mail.from-name:MemoryVault}") String fromName,
            @Value("${app.mail.resend-api-key:}") String resendApiKey,
            @Value("${app.mail.mailgun-api-key:}") String mailgunApiKey,
            @Value("${app.mail.mailgun-domain:}") String mailgunDomain,
            @Value("${app.mail.smtp.host:}") String smtpHost,
            @Value("${app.mail.smtp.port:587}") int smtpPort,
            @Value("${app.mail.smtp.username:}") String smtpUsername,
            @Value("${app.mail.smtp.password:}") String smtpPassword,
            @Value("${app.mail.smtp.auth:true}") boolean smtpAuth,
            @Value("${app.mail.smtp.starttls:true}") boolean smtpStarttls,
            @Value("${app.mail.smtp.ssl:false}") boolean smtpSsl
    ) {
        this.fromAddress = fromAddress != null && !fromAddress.isBlank() ? fromAddress.trim() : "MemoryVault <onboarding@resend.dev>";
        this.fromName = fromName != null && !fromName.isBlank() ? fromName.trim() : "MemoryVault";
        this.resendApiKey = resendApiKey != null ? resendApiKey.trim() : "";
        this.mailgunApiKey = mailgunApiKey != null ? mailgunApiKey.trim() : "";
        this.mailgunDomain = mailgunDomain != null ? mailgunDomain.trim() : "";
        this.smtpHost = smtpHost != null ? smtpHost.trim() : "";
        this.smtpPort = smtpPort > 0 ? smtpPort : 587;
        this.smtpUsername = smtpUsername != null ? smtpUsername.trim() : "";
        this.smtpPassword = smtpPassword != null ? smtpPassword.trim() : "";
        this.smtpAuth = smtpAuth;
        this.smtpStarttls = smtpStarttls;
        this.smtpSsl = smtpSsl;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();

        log.info("[EmailService] Initialized. Resend configured: {}, SMTP configured: {} (host={}), Mailgun configured: {}",
                !this.resendApiKey.isBlank(),
                !this.smtpHost.isBlank(),
                this.smtpHost.isBlank() ? "none" : this.smtpHost,
                !this.mailgunApiKey.isBlank());
    }

    @Override
    public boolean isConfigured() {
        return !resendApiKey.isBlank() || !smtpHost.isBlank() || (!mailgunApiKey.isBlank() && !mailgunDomain.isBlank());
    }

    @Async("intelligenceExecutor")
    @Override
    public CompletableFuture<Boolean> sendPasswordResetEmailAsync(String toEmail, String resetCode) {
        boolean success = sendPasswordResetEmail(toEmail, resetCode);
        return CompletableFuture.completedFuture(success);
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String resetCode) {
        if (toEmail == null || toEmail.isBlank() || !toEmail.contains("@")) {
            log.warn("[EmailService] Cannot send password reset: invalid email address '{}'", toEmail);
            return false;
        }

        String subject = "Reset your MemoryVault password (Code: " + resetCode + ")";
        String htmlContent = buildResetPasswordHtml(toEmail, resetCode);
        String textContent = buildResetPasswordText(toEmail, resetCode);

        return sendHtmlEmail(toEmail, subject, htmlContent, textContent);
    }

    @Override
    public boolean sendHtmlEmail(String toEmail, String subject, String htmlContent, String plainContent) {
        if (!isConfigured()) {
            log.warn("[EmailService] Outbound email requested for {} but no email provider is configured " +
                    "(RESEND_API_KEY, SMTP_HOST, or MAILGUN_API_KEY).", toEmail);
            return false;
        }

        // 1. Try Resend REST API if configured
        if (!resendApiKey.isBlank()) {
            try {
                boolean sent = sendViaResend(toEmail, subject, htmlContent, plainContent);
                if (sent) return true;
                log.warn("[EmailService] Resend dispatch returned false; attempting fallback provider if configured...");
            } catch (Exception e) {
                log.error("[EmailService] Error dispatching email via Resend to {}: {}", toEmail, e.getMessage());
            }
        }

        // 2. Try Standard SMTP if configured (Brevo, Gmail, SendGrid, Mailjet)
        if (!smtpHost.isBlank()) {
            try {
                boolean sent = sendViaSmtp(toEmail, subject, htmlContent, plainContent);
                if (sent) return true;
                log.warn("[EmailService] SMTP dispatch returned false; attempting fallback provider if configured...");
            } catch (Exception e) {
                log.error("[EmailService] Error dispatching email via SMTP ({}:{}) to {}: {}", smtpHost, smtpPort, toEmail, e.getMessage());
            }
        }

        // 3. Try Mailgun REST API if configured
        if (!mailgunApiKey.isBlank() && !mailgunDomain.isBlank()) {
            try {
                boolean sent = sendViaMailgun(toEmail, subject, htmlContent, plainContent);
                if (sent) return true;
            } catch (Exception e) {
                log.error("[EmailService] Error dispatching email via Mailgun to {}: {}", toEmail, e.getMessage());
            }
        }

        log.error("[EmailService] All configured outbound email providers failed for recipient {}", toEmail);
        return false;
    }

    /**
     * Dispatches transactional email via Resend REST API (https://api.resend.com/emails).
     */
    private boolean sendViaResend(String toEmail, String subject, String htmlContent, String plainContent) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("from", fromAddress);
        body.put("to", List.of(toEmail));
        body.put("subject", subject);
        body.put("html", htmlContent);
        if (plainContent != null && !plainContent.isBlank()) {
            body.put("text", plainContent);
        }

        String jsonPayload = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "MemoryVault-OutboundMailer/1.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("[EmailService] Successfully dispatched email to {} via Resend. HTTP status: {}", toEmail, response.statusCode());
            return true;
        } else {
            log.error("[EmailService] Resend API error: status={}, body={}", response.statusCode(), response.body());
            return false;
        }
    }

    /**
     * Dispatches email via standard SMTP session (Brevo, Gmail, SendGrid, etc.).
     */
    private boolean sendViaSmtp(String toEmail, String subject, String htmlContent, String plainContent) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", String.valueOf(smtpAuth));
        if (smtpStarttls) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        if (smtpSsl) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "6000");
        props.put("mail.smtp.timeout", "10000");

        Session session;
        if (smtpAuth && !smtpUsername.isBlank()) {
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        MimeMessage message = new MimeMessage(session);

        // Sender address formatting
        if (fromAddress.contains("<") && fromAddress.contains(">")) {
            int start = fromAddress.indexOf("<");
            int end = fromAddress.indexOf(">");
            String name = fromAddress.substring(0, start).trim();
            String addr = fromAddress.substring(start + 1, end).trim();
            message.setFrom(new InternetAddress(addr, name.isEmpty() ? fromName : name));
        } else {
            message.setFrom(new InternetAddress(fromAddress, fromName));
        }

        message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
        message.setSubject(subject, "UTF-8");

        MimeMultipart multipart = new MimeMultipart("alternative");

        if (plainContent != null && !plainContent.isBlank()) {
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(plainContent, "UTF-8");
            multipart.addBodyPart(textPart);
        }

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlContent, "text/html; charset=UTF-8");
        multipart.addBodyPart(htmlPart);

        message.setContent(multipart);
        Transport.send(message);

        log.info("[EmailService] Successfully dispatched email to {} via SMTP server {}:{}", toEmail, smtpHost, smtpPort);
        return true;
    }

    /**
     * Dispatches transactional email via Mailgun REST API.
     */
    private boolean sendViaMailgun(String toEmail, String subject, String htmlContent, String plainContent) throws Exception {
        String auth = Base64.getEncoder().encodeToString(("api:" + mailgunApiKey).getBytes(StandardCharsets.UTF_8));
        String endpoint = "https://api.mailgun.net/v3/" + mailgunDomain + "/messages";

        StringBuilder formData = new StringBuilder();
        formData.append("from=").append(URLEncoder.encode(fromAddress, StandardCharsets.UTF_8));
        formData.append("&to=").append(URLEncoder.encode(toEmail, StandardCharsets.UTF_8));
        formData.append("&subject=").append(URLEncoder.encode(subject, StandardCharsets.UTF_8));
        formData.append("&html=").append(URLEncoder.encode(htmlContent, StandardCharsets.UTF_8));
        if (plainContent != null && !plainContent.isBlank()) {
            formData.append("&text=").append(URLEncoder.encode(plainContent, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "MemoryVault-OutboundMailer/1.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(formData.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("[EmailService] Successfully dispatched email to {} via Mailgun. HTTP status: {}", toEmail, response.statusCode());
            return true;
        } else {
            log.error("[EmailService] Mailgun API error: status={}, body={}", response.statusCode(), response.body());
            return false;
        }
    }

    /**
     * Generates a responsive HTML email with MemoryVault branding and prominent 6-digit OTP box.
     */
    private String buildResetPasswordHtml(String email, String resetCode) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Reset your MemoryVault password</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #0f172a; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b;">
              <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="background-color: #0f172a; padding: 40px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 520px; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3);">
                      <!-- Header -->
                      <tr>
                        <td style="background-color: #1e293b; padding: 26px 36px; text-align: left; border-bottom: 1px solid #334155;">
                          <table role="presentation" border="0" cellpadding="0" cellspacing="0">
                            <tr>
                              <td style="background-color: #0d9488; width: 36px; height: 36px; border-radius: 10px; text-align: center; vertical-align: middle;">
                                <span style="color: #ffffff; font-weight: 800; font-size: 20px; line-height: 36px; display: inline-block;">M</span>
                              </td>
                              <td style="padding-left: 12px; color: #ffffff; font-size: 20px; font-weight: 700; letter-spacing: -0.5px;">
                                MemoryVault
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <!-- Content -->
                      <tr>
                        <td style="padding: 36px 36px 28px 36px;">
                          <h1 style="margin: 0 0 14px 0; font-size: 22px; font-weight: 700; color: #0f172a; letter-spacing: -0.5px;">
                            Password Reset Code
                          </h1>
                          <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 24px; color: #475569;">
                            We received a request to reset your password for your MemoryVault account (<strong>{{email}}</strong>). Enter the verification code below to set your new password:
                          </p>
                          
                          <!-- Code Box -->
                          <div style="background-color: #f0fdfa; border: 1.5px solid #99f6e4; border-radius: 14px; padding: 24px 20px; text-align: center; margin-bottom: 24px;">
                            <div style="font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 1.5px; color: #0d9488; margin-bottom: 8px;">
                              One-Time Verification Code
                            </div>
                            <div style="font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace; font-size: 38px; font-weight: 800; letter-spacing: 8px; color: #0f766e;">
                              {{resetCode}}
                            </div>
                          </div>

                          <p style="margin: 0 0 16px 0; font-size: 13px; line-height: 20px; color: #64748b;">
                            ⏱️ This code will expire in <strong>15 minutes</strong>. If you did not request this reset, your account is still secure and you can safely ignore this email.
                          </p>
                        </td>
                      </tr>
                      <!-- Divider -->
                      <tr>
                        <td style="padding: 0 36px;">
                          <hr style="border: none; border-top: 1px solid #f1f5f9; margin: 0;">
                        </td>
                      </tr>
                      <!-- Footer -->
                      <tr>
                        <td style="padding: 24px 36px; background-color: #fafafa; font-size: 12px; line-height: 18px; color: #94a3b8; text-align: center;">
                          Sent to {{email}} · <a href="https://memoryvault.stacknode.dev" style="color: #0d9488; text-decoration: none; font-weight: 500;">MemoryVault</a><br>
                          Your personal, intelligent bookmark and knowledge sanctuary.
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """
            .replace("{{email}}", email)
            .replace("{{resetCode}}", resetCode);
    }

    /**
     * Generates plain-text alternative.
     */
    private String buildResetPasswordText(String email, String resetCode) {
        return """
            MemoryVault - Password Reset Code
            
            We received a request to reset your password for {{email}}.
            
            Your 6-digit verification code is:
            {{resetCode}}
            
            This code will expire in 15 minutes.
            
            If you did not request this password reset, please ignore this email.
            
            --
            MemoryVault · https://memoryvault.stacknode.dev
            """
            .replace("{{email}}", email)
            .replace("{{resetCode}}", resetCode);
    }
}
