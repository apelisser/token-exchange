package com.apelisser.tokenexchange.security;

import com.apelisser.tokenexchange.domain.TenantConfig;
import com.apelisser.tokenexchange.repository.TenantConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MultiTenantJwtDecoder implements JwtDecoder {

    private final TenantConfigRepository tenantConfigRepository;

    // cache dos decoders por issuer - evita recriar a cada request
    private final Map<String, JwtDecoder> decoderCache = new ConcurrentHashMap<>();

    @Override
    public Jwt decode(String token) throws JwtException {

        // 1. Extrai o issuer do token sem validar ainda
        String issuer = extractIssuerUnchecked(token);
        log.debug("Decoding token for issuer={}", issuer);

        // 2. Busca o tenant pelo issuer
        TenantConfig tenant = tenantConfigRepository.findByIssuerUri(issuer)
                .orElseThrow(() -> new JwtException("Unknown issuer: " + issuer));

        // 3. Obtém ou cria o decoder para esse tenant
        JwtDecoder decoder = decoderCache.computeIfAbsent(
                issuer,
                iss -> buildDecoder(tenant)
        );

        // 4. Valida o token com o JWKS do KC-01 desse tenant
        return decoder.decode(token);
    }

    private JwtDecoder buildDecoder(TenantConfig tenant) {
        String jwksUri = tenant.getIssuerUri()
                + "/protocol/openid-connect/certs";
        log.info("Building JwtDecoder for tenant={} jwksUri={}",
                tenant.getTenantName(), jwksUri);
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }

    private String extractIssuerUnchecked(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new JwtException("Invalid JWT format");
            }
            String payload = new String(
                    java.util.Base64.getUrlDecoder().decode(parts[1])
            );
            String issuer = payload.replaceAll(".*\"iss\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            if (issuer.equals(payload)) {
                throw new JwtException("Missing iss claim");
            }
            return issuer;
        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtException("Failed to parse token: " + e.getMessage());
        }
    }
}