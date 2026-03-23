package com.apelisser.tokenexchange.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private static final Map<String, String> STATUS_UP = Map.of("status", "UP");

    @GetMapping
    public Map<String, String> health() {
        return STATUS_UP;
    }

}
