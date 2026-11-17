package dev.orbit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher(1_000);

    @Test
    void verifiesTheOriginalPasswordOnly() {
        String stored = hasher.hash("correct horse battery staple");
        assertThat(hasher.verify("correct horse battery staple", stored)).isTrue();
        assertThat(hasher.verify("correct horse battery stapl", stored)).isFalse();
        assertThat(hasher.verify("", stored)).isFalse();
    }

    @Test
    void usesAFreshSaltForEveryHash() {
        assertThat(hasher.hash("same password")).isNotEqualTo(hasher.hash("same password"));
    }

    @Test
    void neverStoresThePlaintext() {
        assertThat(hasher.hash("hunter2hunter2")).doesNotContain("hunter2").startsWith("pbkdf2-sha256$1000$");
    }

    @Test
    void rejectsMalformedOrUnknownFormatsWithoutThrowing() {
        assertThat(hasher.verify("x", null)).isFalse();
        assertThat(hasher.verify("x", "plaintext")).isFalse();
        assertThat(hasher.verify("x", "bcrypt$10$a$b")).isFalse();
        assertThat(hasher.verify("x", "pbkdf2-sha256$notanumber$YQ$YQ")).isFalse();
        assertThat(hasher.verify("x", "pbkdf2-sha256$1000$***$***")).isFalse();
    }

    @Test
    void refusesWeakIterationCounts() {
        assertThatThrownBy(() -> new PasswordHasher(10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionTokenDigestIsStableHex() {
        assertThat(PasswordHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
