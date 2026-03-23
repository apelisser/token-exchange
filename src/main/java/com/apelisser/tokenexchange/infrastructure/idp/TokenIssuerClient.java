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
public class TokenIssuerClient {

    private final RestClient restClient;

    public String getInternalToken(TenantConfig tenant) {
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", tenant.getInternalClientId());
            body.add("client_secret", tenant.getInternalClientSecret());

            Map<?, ?> response = restClient.post()
                .uri(tenant.getInternalTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(Map.class);

            String accessToken = response != null
                ? (String) response.get("access_token")
                : null;

            if (accessToken == null) {
                throw new TokenExchangeException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "exchange_failed",
                    "No access_token in KC-02 response"
                );
            }

            log.info("Internal token obtained for tenant={}", tenant.getTenantName());
            return accessToken;

        } catch (TokenExchangeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get internal token for tenant={}", tenant.getTenantName(), e);
            throw new TokenExchangeException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "exchange_failed",
                "Failed to get internal token: " + e.getMessage()
            );
        }
    }
}