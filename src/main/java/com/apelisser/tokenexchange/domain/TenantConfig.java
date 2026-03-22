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

    /**
     * Tenant identifier name.
     * Used only for log and internal management purposes.
     */
    @Column(nullable = false, unique = true)
    private String tenantName;

    /**
     * The issuer URI of the external KC (client).
     * Must exactly match the "iss" claim of the received token.
     * Used to identify the tenant from the token.
     * Example: http://kc-client.com/realms/poc-realm
     */
    @Column(nullable = false, unique = true)
    private String issuerUri;

    /**
     * The URL of the introspection endpoint of the external KC.
     * Used to validate if the token has been revoked when the lifetime
     * exceeds the threshold defined in maxTokenLifetimeMinutes.
     * Example: http://kc-client.com/realms/poc-realm/protocol/openid-connect/token/introspect
     */
    @Column(nullable = false)
    private String introspectionUri;

    /**
     * Confidential client ID in the external KC authorized to perform introspection.
     * Must have introspection permission in the client's KC.
     * Example: kc-b-broker
     */
    @Column(nullable = false)
    private String externalClientId;

    /**
     * Client secret of the externalClientId.
     * In production, it should be encrypted.
     */
    @Column(nullable = false)
    private String externalClientSecret;

    /**
     * URL of the token endpoint of the internal KC (yours).
     * Used to obtain Token B via client_credentials.
     * Example: http://localhost:8080/realms/my-realm/protocol/openid-connect/token
     */
    @Column(nullable = false)
    private String internalTokenUri;

    /**
     * Client ID in the internal KC for this tenant.
     * Each tenant has its own client in the internal KC.
     * Must have Service Account enabled (client_credentials).
     * Example: my-client-tenant-a
     */
    @Column(nullable = false)
    private String internalClientId;

    /**
     * Client secret of the internalClientId.
     * In production, it should be encrypted.
     */
    @Column(nullable = false)
    private String internalClientSecret;

    /**
     * URL of the JWKS of the external KC for local validation of the token signature.
     * If filled in, uses this URL directly.
     * If null or empty, tries to discover via the discovery endpoint ({issuerUri}/.well-known/openid-configuration).
     * Fill in explicitly for IDPs that do not follow the discovery standard.
     * Example: http://kc-client.com/realms/poc-realm/protocol/openid-connect/certs
     */
    @Column
    private String jwksUri;

    /**
     * Threshold in minutes to decide between JWKS and Introspection.
     * Tokens with lifetime (exp - iat) above this value perform additional introspection
     * to ensure they have not been revoked.
     * Tokens below the threshold are validated only via JWKS (faster).
     * Recommended value: 15
     */
    @Column(nullable = false)
    private Long maxTokenLifetimeMinutes;

}