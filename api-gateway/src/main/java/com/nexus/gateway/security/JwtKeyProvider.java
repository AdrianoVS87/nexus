package com.nexus.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Manages the RSA keypair used for JWT signing and verification.
 *
 * <p>When a keypair file path is configured, the provider persists the
 * generated keypair to disk so that tokens survive gateway restarts.
 * When no path is configured, keys are generated in memory (dev mode).</p>
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    public JwtKeyProvider(
            @Value("${gateway.jwt.keypair-path:}") String keypairPath) {
        try {
            KeyPair keyPair;
            if (keypairPath != null && !keypairPath.isBlank()) {
                keyPair = loadOrGenerate(Path.of(keypairPath));
            } else {
                log.info("No gateway.jwt.keypair-path configured — generating ephemeral RSA keypair");
                keyPair = generateKeyPair();
            }
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JWT keypair", e);
        }
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    private KeyPair loadOrGenerate(Path path) throws Exception {
        Path pubPath = path.resolveSibling(path.getFileName() + ".pub");
        if (Files.exists(path) && Files.exists(pubPath)) {
            log.info("Loading JWT keypair from {}", path);
            return loadKeyPair(path, pubPath);
        }
        log.info("Generating new JWT keypair at {}", path);
        KeyPair keyPair = generateKeyPair();
        saveKeyPair(keyPair, path, pubPath);
        return keyPair;
    }

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static void saveKeyPair(KeyPair keyPair, Path privatePath, Path publicPath) throws IOException {
        Files.createDirectories(privatePath.getParent());
        Files.writeString(privatePath, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        Files.writeString(publicPath, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
    }

    private static KeyPair loadKeyPair(Path privatePath, Path publicPath) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");

        byte[] privateBytes = Base64.getDecoder().decode(Files.readString(privatePath).trim());
        PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));

        byte[] publicBytes = Base64.getDecoder().decode(Files.readString(publicPath).trim());
        PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(publicBytes));

        return new KeyPair(publicKey, privateKey);
    }
}
