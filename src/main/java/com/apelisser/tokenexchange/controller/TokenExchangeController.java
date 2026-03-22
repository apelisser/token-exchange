package com.apelisser.tokenexchange.controller;

import com.apelisser.tokenexchange.exception.TokenExchangeException;
import com.apelisser.tokenexchange.service.TokenExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/exchange")
@RequiredArgsConstructor
public class TokenExchangeController {

    private final TokenExchangeService tokenExchangeService;

    @PostMapping
    public ResponseEntity<?> exchange(
        @RequestHeader("Authorization") String authorizationHeader) {
        try {
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401)
                    .body(Map.of("error", "invalid_request",
                        "error_description", "Missing or invalid Authorization header"));
            }

            String tokenA = authorizationHeader.substring(7);
            String tokenB = tokenExchangeService.exchange(tokenA);

            return ResponseEntity.ok(Map.of(
                "access_token", tokenB,
                "token_type", "Bearer"
            ));

        } catch (TokenExchangeException e) {
            log.warn("Token exchange failed: {}", e.getMessage());
            return ResponseEntity.status(e.getStatus())
                .body(Map.of("error", e.getError(),
                    "error_description", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during token exchange", e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "server_error",
                    "error_description", e.getMessage()));
        }
    }
}
