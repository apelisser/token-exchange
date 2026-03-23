package com.apelisser.tokenexchange.infrastructure.idp;

import com.apelisser.tokenexchange.domain.TenantConfig;
import com.apelisser.tokenexchange.domain.TokenExchangeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenIntrospectionClient {

    private final RestClient restClient;

    public boolean introspectToken(String token, TenantConfig tenant) {
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", token);
            body.add("client_id", tenant.getExternalClientId());
            body.add("client_secret", tenant.getExternalClientSecret());

            Map<?, ?> response = restClient.post()
                .uri(tenant.getIntrospectionUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(Map.class);

            boolean active = Boolean.TRUE.equals(
                response != null ? response.get("active") : false
            );
            log.info("Introspection for tenant={} active={}", tenant.getTenantName(), active);
            return active;

        } catch (Exception e) {
            log.error("Introspection failed for tenant={}", tenant.getTenantName(), e);
            return false;
        }
    }

}