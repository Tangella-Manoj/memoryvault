package io.memoryvault.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    /** HMAC keys shorter than this are rejected at startup — 256 bits is the HS256 floor. */
    private static final int MIN_SECRET_BITS = 256;

    private final SecretKey key;
    private final long accessExpiryMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiry-ms}") long accessExpiryMs
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        int bits = secretBytes.length * 8;
        if (bits < MIN_SECRET_BITS) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) is " + bits + " bits; it must be at least "
                            + MIN_SECRET_BITS + " bits. Generate one with: openssl rand -hex 32");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessExpiryMs = accessExpiryMs;
    }

    public String generateAccessToken(Long userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiryMs))
                .signWith(key)
                .compact();
    }

    public Long extractUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }

    public String extractRole(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
