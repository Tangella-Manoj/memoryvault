package io.memoryvault.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCipherTest {

    @Test
    void encryptThenDecrypt_roundTripsExactly() {
        TokenCipher cipher = new TokenCipher("c143324a08021aad9ef6bdbef61186124230115307a1d7b50c5312cda50f1a3e");

        String plaintext = "ya29.a0AfH6SMC-fake-access-token-value";
        String encrypted = cipher.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void sameInputEncryptsDifferently_dueToRandomIv() {
        TokenCipher cipher = new TokenCipher("c143324a08021aad9ef6bdbef61186124230115307a1d7b50c5312cda50f1a3e");

        String a = cipher.encrypt("same-value");
        String b = cipher.encrypt("same-value");

        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo("same-value");
        assertThat(cipher.decrypt(b)).isEqualTo("same-value");
    }
}
