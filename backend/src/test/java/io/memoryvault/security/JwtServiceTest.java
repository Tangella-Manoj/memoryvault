package io.memoryvault.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class JwtServiceTest {

    @Test
    void rejectsSecretShorterThan256Bits() {
        String tooShort = "short-secret"; // 12 bytes = 96 bits
        assertThatThrownBy(() -> new JwtService(tooShort, 86400000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256");
    }

    @Test
    void acceptsSecretAtOrAbove256Bits() {
        String exactly256Bits = "01234567890123456789012345678901"; // 33 bytes >= 32
        assertThatCode(() -> new JwtService(exactly256Bits, 86400000L)).doesNotThrowAnyException();
    }
}
