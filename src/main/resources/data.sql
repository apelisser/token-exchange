INSERT INTO tenant_config (
    tenant_name,
    issuer_uri,
    introspection_uri,
    external_client_id,
    external_client_secret,
    internal_token_uri,
    internal_client_id,
    internal_client_secret,
    jwks_uri,
    max_token_lifetime_minutes
) VALUES (
    'poc-tenant',
    'http://host.docker.internal:8081/realms/poc-realm',
    'http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/token/introspect',
    'kc-b-broker',
    'kloXybz2j6ftFFoAeOEd6NwPfPr0oupQ',
    'http://host.docker.internal:8082/realms/my-realm/protocol/openid-connect/token',
    'my-client',
    '3MtIVPE3PdRFCJ8v13nOy9rRGDyhimcL',
    'http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/certs',
    15
);
