package com.apelisser.tokenexchange.domain;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TokenExchangeException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    public TokenExchangeException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }
}
