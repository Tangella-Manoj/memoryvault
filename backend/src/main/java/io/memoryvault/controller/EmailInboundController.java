package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.exception.ApiException;
import io.memoryvault.service.EmailIngestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Mailgun's inbound-routing webhook path (Upgrade 3). This project has no Mailgun
 * credentials configured — {@link io.memoryvault.service.SmtpReceiverConfig}'s self-hosted
 * SMTP receiver is the path actually exercised end to end — but this endpoint is built
 * to the real contract so switching providers later is a config change, not a rewrite.
 */
@RestController
@RequestMapping("/api/email")
public class EmailInboundController {

    private final EmailIngestionService emailIngestionService;
    private final String signingKey;

    public EmailInboundController(
            EmailIngestionService emailIngestionService,
            @Value("${app.mailgun.webhook-signing-key:}") String signingKey
    ) {
        this.emailIngestionService = emailIngestionService;
        this.signingKey = signingKey;
    }

    /**
     * @param recipient  the To address Mailgun routed the message to
     * @param subject    email subject
     * @param bodyPlain  the plain-text body, if any
     * @param bodyHtml   the HTML body, if any
     * @param timestamp  Mailgun's webhook signature fields — verified when a signing key
     *                   is configured; if none is configured (this project's actual state),
     *                   verification is skipped, since there is nothing to check against
     * @param token      see {@code timestamp}
     * @param signature  see {@code timestamp}
     */
    @PostMapping(path = "/inbound", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ApiResponse<Map<String, Object>> inbound(
            @RequestParam String recipient,
            @RequestParam(required = false, defaultValue = "") String subject,
            @RequestParam(name = "body-plain", required = false) String bodyPlain,
            @RequestParam(name = "body-html", required = false) String bodyHtml,
            @RequestParam(required = false) String timestamp,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String signature
    ) {
        if (!signingKey.isBlank()) {
            verifySignature(timestamp, token, signature);
        }

        int created = emailIngestionService.ingest(recipient, subject, bodyPlain, bodyHtml);
        return ApiResponse.success(Map.of("itemsCreated", created), "Email processed");
    }

    private void verifySignature(String timestamp, String token, String signature) {
        if (timestamp == null || token == null || signature == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Missing Mailgun signature fields");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal((timestamp + token).getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);

            if (!MessageDigest.isEqual(computedHex.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Mailgun webhook signature did not match");
            }
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
