package com.nexus.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JwtKeyProviderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("creates keypair files when path is configured")
    void createsKeypairFiles() {
        Path keyPath = tempDir.resolve("jwt-keypair");

        JwtKeyProvider provider = new JwtKeyProvider(keyPath.toString());

        assertThat(provider.getPrivateKey()).isNotNull();
        assertThat(provider.getPublicKey()).isNotNull();
        assertThat(Files.exists(keyPath)).isTrue();
        assertThat(Files.exists(keyPath.resolveSibling("jwt-keypair.pub"))).isTrue();
    }

    @Test
    @DisplayName("loads existing keypair and keeps key material stable across restarts")
    void loadsExistingKeypair() {
        Path keyPath = tempDir.resolve("jwt-keypair");

        JwtKeyProvider first = new JwtKeyProvider(keyPath.toString());
        String firstPrivate = java.util.Base64.getEncoder().encodeToString(first.getPrivateKey().getEncoded());
        String firstPublic = java.util.Base64.getEncoder().encodeToString(first.getPublicKey().getEncoded());

        JwtKeyProvider second = new JwtKeyProvider(keyPath.toString());
        String secondPrivate = java.util.Base64.getEncoder().encodeToString(second.getPrivateKey().getEncoded());
        String secondPublic = java.util.Base64.getEncoder().encodeToString(second.getPublicKey().getEncoded());

        assertThat(secondPrivate).isEqualTo(firstPrivate);
        assertThat(secondPublic).isEqualTo(firstPublic);
    }

    @Test
    @DisplayName("falls back to ephemeral keys when path is empty")
    void ephemeralMode() {
        JwtKeyProvider provider = new JwtKeyProvider("");

        assertThat(provider.getPrivateKey()).isNotNull();
        assertThat(provider.getPublicKey()).isNotNull();
    }
}
