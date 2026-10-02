package io.memoryvault.service;

import io.memoryvault.repository.PasswordResetTokenRepository;
import io.memoryvault.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenCleanupServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private TokenCleanupService tokenCleanupService;

    @Test
    void purgeExpiredTokens_callsBothRepositories() {
        tokenCleanupService.purgeExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredAndRevoked(any(Instant.class));
        verify(passwordResetTokenRepository).deleteExpiredAndUsed(any(Instant.class));
    }
}
