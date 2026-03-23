package com.apelisser.tokenexchange.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantConfigRepository extends JpaRepository<TenantConfig, Long> {
    Optional<TenantConfig> findByIssuerUri(String issuerUri);
}
