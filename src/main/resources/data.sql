INSERT INTO tenant_config (
    id,
    tenant_name,
    issuer_uri,
    external_client_id,
    external_client_secret,
    internal_token_uri,
    internal_client_id,
    internal_client_secret
) VALUES (
    1,
    'mock-tenant',
    'http://192.168.1.250:18081/realms/poc-realm',
    'kc-b-broker',
    'ThaQTvXSm3tRLwnlmuxx0jT4tNGrHhrI',
    'http://localhost:8082/realms/my-realm/protocol/openid-connect/token',
    'my-client',
    'CbaEMRYUwWh7CXKfkfNvuxdunBWqhR00'
);