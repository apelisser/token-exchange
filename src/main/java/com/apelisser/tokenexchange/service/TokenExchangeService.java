package com.apelisser.tokenexchange.service;

import com.apelisser.tokenexchange.client.KeycloakClient;
import com.apelisser.tokenexchange.domain.TenantConfig;
import com.apelisser.tokenexchange.exception.TokenExchangeException;
import com.apelisser.tokenexchange.repository.TenantConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExchangeService {

    private final KeycloakClient keycloakClient;
    private final TenantConfigRepository tenantConfigRepository;

    public String exchange(String tokenA) {

        // 1. Extrai o issuer do token
        String issuer = extractIssuer(tokenA);
        log.info("Token exchange requested for issuer={}", issuer);

        // 2. Busca configuração do tenant pelo issuer
        TenantConfig tenant = tenantConfigRepository.findByIssuerUri(issuer)
            .orElseThrow(() -> new TokenExchangeException(
                HttpStatus.UNAUTHORIZED,
                "unknown_issuer",
                "No tenant configured for issuer: " + issuer
            ));

        // 3. Valida Token A via introspection no KC externo
        boolean valid = keycloakClient.introspectToken(tokenA, tenant);
        if (!valid) {
            throw new TokenExchangeException(
                HttpStatus.UNAUTHORIZED,
                "invalid_token",
                "Token A is invalid or expired"
            );
        }

        // 4. Obtém Token B do KC interno
        return keycloakClient.getInternalToken(tenant);
    }

    private String extractIssuer(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new TokenExchangeException(
                    HttpStatus.UNAUTHORIZED,
                    "invalid_token",
                    "Invalid JWT format"
                );
            }
            String payload = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
            );
            // extrai o iss do payload JSON
            String issuer = payload.replaceAll(".*\"iss\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            if (issuer.equals(payload)) {
                throw new TokenExchangeException(
                    HttpStatus.UNAUTHORIZED,
                    "invalid_token",
                    "Missing iss claim in token"
                );
            }
            return issuer;
        } catch (TokenExchangeException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenExchangeException(
                HttpStatus.UNAUTHORIZED,
                "invalid_token",
                "Failed to parse token: " + e.getMessage()
            );
        }
    }
}
