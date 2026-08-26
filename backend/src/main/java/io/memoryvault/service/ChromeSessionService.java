package io.memoryvault.service;

import io.memoryvault.domain.ChromeSession;
import io.memoryvault.domain.User;
import io.memoryvault.repository.ChromeSessionRepository;
import io.memoryvault.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
public class ChromeSessionService {

    private static final long SESSION_DAYS = 30;

    private final ChromeSessionRepository chromeSessionRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ChromeSessionService(ChromeSessionRepository chromeSessionRepository, UserRepository userRepository) {
        this.chromeSessionRepository = chromeSessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public String createSession(Long userId) {
        User user = userRepository.getReferenceById(userId);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        ChromeSession session = ChromeSession.builder()
                .user(user)
                .sessionToken(token)
                .expiresAt(Instant.now().plus(SESSION_DAYS, ChronoUnit.DAYS))
                .build();
        chromeSessionRepository.save(session);

        return token;
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolveUserId(String token) {
        return chromeSessionRepository.findBySessionToken(token)
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .map(s -> s.getUser().getId());
    }
}
