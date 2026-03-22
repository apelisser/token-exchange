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

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExchangeService {

    private final KeycloakClient keycloakClient;
    private final TenantConfigRepository tenantConfigRepository;

    public String exchange(Jwt jwt) {
        String issuer = jwt.getIssuer().toString();
        String username = jwt.getClaimAsString("preferred_username");
        log.info("Token exchange for issuer={} user={}", issuer, username);

        TenantConfig tenant = tenantConfigRepository.findByIssuerUri(issuer)
            .orElseThrow(() -> new TokenExchangeException(
                HttpStatus.UNAUTHORIZED,
                "unknown_issuer",
                "No tenant configured for issuer: " + issuer
            ));

        return keycloakClient.getInternalToken(tenant);
    }
}
