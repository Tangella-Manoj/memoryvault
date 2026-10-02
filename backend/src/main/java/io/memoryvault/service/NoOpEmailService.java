package io.memoryvault.service;

import java.util.concurrent.CompletableFuture;

/**
 * No-op implementation of {@link EmailService} used as a safe default/fallback
 * or in testing environments when external email dispatch is not active.
 */
public class NoOpEmailService implements EmailService {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> sendPasswordResetEmailAsync(String toEmail, String resetCode) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String resetCode) {
        return false;
    }

    @Override
    public boolean sendHtmlEmail(String toEmail, String subject, String htmlContent, String plainContent) {
        return false;
    }
}
