package io.memoryvault.service.youtube;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps an opaque OAuth {@code state} nonce to the user who initiated the flow. Google's
 * callback redirect is a plain browser GET with no way to attach our JWT, so we can't
 * identify the user from the callback request itself — the state param is how the
 * round trip carries that identity. Deliberately an opaque random nonce rather than the
 * raw user id: a client-visible {@code state=42} would let anyone link their own OAuth
 * grant to a different user's account by editing the redirect URL.
 */
@Component
public class OAuthStateStore {

    private static final long TTL_SECONDS = 600; // 10 minutes — comfortably covers a real consent flow

    private record Entry(Long userId, Instant expiresAt) {
    }

    private final Map<String, Entry> states = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public String create(Long userId) {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(state, new Entry(userId, Instant.now().plusSeconds(TTL_SECONDS)));
        return state;
    }

    /** Consumes (single-use) and returns the user id for a state, if valid and unexpired. */
    public Optional<Long> consume(String state) {
        Entry entry = states.remove(state);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }
}
