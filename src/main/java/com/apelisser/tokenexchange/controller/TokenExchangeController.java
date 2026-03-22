package com.apelisser.tokenexchange.controller;

import com.apelisser.tokenexchange.exception.TokenExchangeException;
import com.apelisser.tokenexchange.service.TokenExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/exchange")
@RequiredArgsConstructor
public class TokenExchangeController {

    private final TokenExchangeService tokenExchangeService;

    @PostMapping
    public ResponseEntity<?> exchange(@AuthenticationPrincipal Jwt jwt) {
        try {
            String tokenB = tokenExchangeService.exchange(jwt);
            return ResponseEntity.ok(Map.of(
                "access_token", tokenB,
                "token_type", "Bearer"
            ));
        } catch (TokenExchangeException e) {
            log.warn("Token exchange failed: {}", e.getMessage());
            return ResponseEntity.status(e.getStatus())
                .body(Map.of(
                    "error", e.getError(),
                    "error_description", e.getMessage()
                ));
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "server_error",
                    "error_description", e.getMessage()));
        }
    }
}
