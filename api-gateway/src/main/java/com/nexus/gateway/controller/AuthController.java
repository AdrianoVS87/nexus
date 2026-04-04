package com.nexus.gateway.controller;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.gateway.security.JwtKeyProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtKeyProvider keyProvider;

    public AuthController(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @PostMapping("/token")
    public Mono<Map<String, String>> generateToken(@RequestBody(required = false) Map<String, String> request) {
        try {
            String subject = (request != null && request.containsKey("subject"))
                    ? request.get("subject")
                    : "test-user";

            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer("nexus-api-gateway")
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.RS256),
                    claims);
            signedJWT.sign(new RSASSASigner(keyProvider.getPrivateKey()));

            return Mono.just(Map.of(
                    "token", signedJWT.serialize(),
                    "expiresIn", "3600",
                    "tokenType", "Bearer"));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to generate token", e));
        }
    }
}
