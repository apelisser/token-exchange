package com.apelisser.tokenexchange.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "tenant_config")
public class TenantConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tenantName;

    @Column(nullable = false, unique = true)
    private String issuerUri;

    @Column(nullable = false)
    private String externalClientId;

    @Column(nullable = false)
    private String externalClientSecret;

    @Column(nullable = false)
    private String internalTokenUri;

    @Column(nullable = false)
    private String internalClientId;

    @Column(nullable = false)
    private String internalClientSecret;

}
