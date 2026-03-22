package com.apelisser.tokenexchange.service;

import com.apelisser.tokenexchange.client.KeycloakClient;
import com.apelisser.tokenexchange.domain.TenantConfig;
import com.apelisser.tokenexchange.exception.TokenExchangeException;
import com.apelisser.tokenexchange.repository.TenantConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExchangeService {

    private final KeycloakClient keycloakClient;
    private final TenantConfigRepository tenantConfigRepository;

    public String exchange(Jwt jwt, String rawToken) {
        String issuer = jwt.getIssuer().toString();
        String username = jwt.getClaimAsString("preferred_username");
        log.info("Token exchange for issuer={} user={}", issuer, username);

        // 1. Find tenant by issuer
        TenantConfig tenant = tenantConfigRepository.findByIssuerUri(issuer)
            .orElseThrow(() -> new TokenExchangeException(
                HttpStatus.UNAUTHORIZED,
                "unknown_issuer",
                "No tenant configured for issuer: " + issuer
            ));

        // 2. Check if introspection is required
        if (requiresIntrospection(jwt, tenant)) {
            log.info("Token lifetime exceeds threshold - performing introspection for tenant={}",
                tenant.getTenantName());
            boolean active = keycloakClient.introspectToken(rawToken, tenant);
            if (!active) {
                throw new TokenExchangeException(
                    HttpStatus.UNAUTHORIZED,
                    "invalid_token",
                    "Token is invalid or revoked"
                );
            }
        } else {
            log.info("Token lifetime within threshold - skipping introspection for tenant={}",
                tenant.getTenantName());
        }

        // 3. Get Token B from internal KC
        return keycloakClient.getInternalToken(tenant);
    }

    private boolean requiresIntrospection(Jwt jwt, TenantConfig tenant) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();

        if (issuedAt == null || expiresAt == null) {
            // no time information - perform introspection for security
            return true;
        }

        long lifetimeMinutes = ChronoUnit.MINUTES.between(issuedAt, expiresAt);
        log.debug("Token lifetime={}min threshold={}min",
            lifetimeMinutes, tenant.getMaxTokenLifetimeMinutes());

        return lifetimeMinutes > tenant.getMaxTokenLifetimeMinutes();
    }
}