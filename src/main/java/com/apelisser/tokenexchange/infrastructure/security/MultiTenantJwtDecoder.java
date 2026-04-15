package com.apelisser.tokenexchange.infrastructure.security;

import com.apelisser.tokenexchange.domain.TenantConfig;
import com.apelisser.tokenexchange.domain.TenantConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MultiTenantJwtDecoder implements JwtDecoder {

    private final TenantConfigRepository tenantConfigRepository;

    // cache of decoders by issuer - avoid recreating at each request
    private final Map<String, JwtDecoder> decoderCache = new ConcurrentHashMap<>();

    @Override
    public Jwt decode(String token) throws JwtException {

        // 1. Extract the issuer from the token without validating yet
        String issuer = extractIssuerUnchecked(token);
        log.debug("Decoding token for issuer={}", issuer);

        // 2. Look up the tenant by issuer
        TenantConfig tenant = tenantConfigRepository.findByIssuerUri(issuer)
            .orElseThrow(() -> new JwtException("Unknown issuer: " + issuer));

        // 3. Obtain or create the decoder for this tenant
        JwtDecoder decoder = decoderCache.computeIfAbsent(
            issuer,
            iss -> buildDecoder(tenant)
        );

        // 4. Validate the token with the JWKS of KC-01 for that tenant
        return decoder.decode(token);
    }

    private JwtDecoder buildDecoder(TenantConfig tenant) {
        if (tenant.getJwksUri() != null && !tenant.getJwksUri().isBlank()) {
            // use the explicit URL registered
            log.info("Building JwtDecoder for tenant={} jwksUri={}", tenant.getTenantName(), tenant.getJwksUri());
            NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(tenant.getJwksUri()).build();

            // set the validator to ensure issuer and other claims are validated
            OAuth2TokenValidator<Jwt> jwtValidator = JwtValidators.createDefaultWithIssuer(tenant.getIssuerUri());
            jwtDecoder.setJwtValidator(jwtValidator);
            return jwtDecoder;
        }

        // fallback: try via discovery
        log.info("Building JwtDecoder for tenant={} via discovery issuer={}", tenant.getTenantName(), tenant.getIssuerUri());
        return JwtDecoders.fromIssuerLocation(tenant.getIssuerUri());
    }

    private String extractIssuerUnchecked(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new JwtException("Invalid JWT format");
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
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