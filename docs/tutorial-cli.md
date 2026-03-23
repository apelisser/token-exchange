# Keycloak configuration - CLI tutorial

> **Prerequisites:**
> - Docker and Docker Compose installed
> - `curl` and `jq` installed
> - `git` installed (used to locate the project root)
> - Entry `127.0.0.1 host.docker.internal` added to `/etc/hosts`

**Add `host.docker.internal` to `/etc/hosts` (if not already present)**

```bash
echo "127.0.0.1 host.docker.internal" | sudo tee -a /etc/hosts
```

**Ensure you are at the project root before running any command:**

```bash
cd "$(git rev-parse --show-toplevel)"
```

**Start the Keycloaks before proceeding:**

```bash
docker compose -f docs/docker-compose.yml up -d keycloak-01 keycloak-02
```

> After starting the containers, wait until both Keycloaks are fully ready before running the curl commands below. You can check readiness with:

```bash
# Wait for KC-01
until curl -s -o /dev/null -w "%{http_code}" \
  http://host.docker.internal:8081/realms/master | grep -q "200"; do
  echo "Waiting for KC-01..."; sleep 3
done && echo "KC-01 ready"

# Wait for KC-02
until curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:8082/realms/master | grep -q "200"; do
  echo "Waiting for KC-02..."; sleep 3
done && echo "KC-02 ready"
```

> Run each block in order. Variables set in earlier blocks (like `$KC_B_BROKER_SECRET`) are reused in later ones - keep the same terminal session.

---

## KC-01 - http://host.docker.internal:8081

### Block 1 - Admin token

```bash
ADMIN_TOKEN_KC01=$(curl -s -X POST \
  'http://host.docker.internal:8081/realms/master/protocol/openid-connect/token' \
  -d "client_id=admin-cli" \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  | jq -r .access_token) && echo "Token KC01: OK"
```

### Block 2 - Create realm

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN_KC01" \
  -H "Content-Type: application/json" \
  -d '{"realm": "poc-realm", "enabled": true}' \
  "http://host.docker.internal:8081/admin/realms" && echo "Realm: OK"
```

### Block 3 - Create client-app (public)

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN_KC01" \
  -H "Content-Type: application/json" \
  -d '{"clientId": "client-app", "enabled": true, "publicClient": true, "directAccessGrantsEnabled": true}' \
  "http://host.docker.internal:8081/admin/realms/poc-realm/clients" && echo "client-app: OK"
```

### Block 4 - Create kc-b-broker (confidential)

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN_KC01" \
  -H "Content-Type: application/json" \
  -d '{"clientId": "kc-b-broker", "enabled": true, "publicClient": false, "clientAuthenticatorType": "client-secret", "standardFlowEnabled": true, "redirectUris": ["http://localhost:8082/realms/my-realm/broker/kc-01/endpoint"]}' \
  "http://host.docker.internal:8081/admin/realms/poc-realm/clients" && echo "kc-b-broker: OK"
```

### Block 5 - Create user

```bash
USER_ID=$(curl -s -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN_KC01" \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "email": "user@example.com", "firstName": "User", "lastName": "One", "enabled": true, "emailVerified": true, "requiredActions": []}' \
  -D - \
  "http://host.docker.internal:8081/admin/realms/poc-realm/users" \
  | grep -i location | awk -F'/' '{print $NF}' | tr -d '\r') && echo "User ID: $USER_ID"
```

### Block 6 - Set password

```bash
curl -s -X PUT \
  -H "Authorization: Bearer $ADMIN_TOKEN_KC01" \
  -H "Content-Type: application/json" \
  -d '{"type": "password", "value": "123", "temporary": false}' \
  "http://host.docker.internal:8081/admin/realms/poc-realm/users/$USER_ID/reset-password" && echo "Password: OK"
```

### Block 7 - Get kc-b-broker secret

```bash
KC_B_BROKER_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN_KC01" \
  'http://host.docker.internal:8081/admin/realms/poc-realm/clients?clientId=kc-b-broker' \
  | jq -r '.[0].id') && \
KC_B_BROKER_SECRET=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN_KC01" \
  "http://host.docker.internal:8081/admin/realms/poc-realm/clients/$KC_B_BROKER_ID/client-secret" \
  | jq -r '.value') && echo "kc-b-broker secret: $KC_B_BROKER_SECRET"
```

---

## KC-02 - http://localhost:8082

### Block 1 - Admin token

```bash
ADMIN_TOKEN_KC02=$(curl -s -X POST \
  'http://localhost:8082/realms/master/protocol/openid-connect/token' \
  -d "client_id=admin-cli" \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  | jq -r .access_token) && echo "Token KC02: OK"
```

### Block 2 - Create realm

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN_KC02" \
  -H "Content-Type: application/json" \
  -d '{"realm": "my-realm", "enabled": true}' \
  "http://localhost:8082/admin/realms" && echo "Realm: OK"
```

### Block 3 - Create Identity Provider

> Uses `$KC_B_BROKER_SECRET` set in KC-01 Block 7.

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN_KC02" \
  -H "Content-Type: application/json" \
  -d "{
    \"alias\": \"kc-01\",
    \"providerId\": \"oidc\",
    \"enabled\": true,
    \"config\": {
      \"clientId\": \"kc-b-broker\",
      \"clientSecret\": \"$KC_B_BROKER_SECRET\",
      \"clientAuthMethod\": \"client_secret_post\",
      \"issuer\": \"http://host.docker.internal:8081/realms/poc-realm\",
      \"authorizationUrl\": \"http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/auth\",
      \"tokenUrl\": \"http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/token\",
      \"userInfoUrl\": \"http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/userinfo\",
      \"jwksUrl\": \"http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/certs\",
      \"logoutUrl\": \"http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/logout\",
      \"tokenIntrospectionUrl\": \"http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/token/introspect\",
      \"metadataDescriptorUrl\": \"http://host.docker.internal:8081/realms/poc-realm/.well-known/openid-configuration\",
      \"validateSignature\": \"true\",
      \"useJwksUrl\": \"true\",
      \"pkceEnabled\": \"false\"
    }
  }" \
  "http://localhost:8082/admin/realms/my-realm/identity-provider/instances" && echo "IDP: OK"
```

### Block 4 - Create my-client

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN_KC02" \
  -H "Content-Type: application/json" \
  -d '{"clientId": "my-client", "enabled": true, "publicClient": false, "clientAuthenticatorType": "client-secret", "serviceAccountsEnabled": true, "standardFlowEnabled": false, "directAccessGrantsEnabled": false}' \
  "http://localhost:8082/admin/realms/my-realm/clients" && echo "my-client: OK"
```

### Block 5 - Get my-client secret

```bash
MY_CLIENT_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN_KC02" \
  'http://localhost:8082/admin/realms/my-realm/clients?clientId=my-client' \
  | jq -r '.[0].id') && \
MY_CLIENT_SECRET=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN_KC02" \
  "http://localhost:8082/admin/realms/my-realm/clients/$MY_CLIENT_ID/client-secret" \
  | jq -r '.value') && echo "my-client secret: $MY_CLIENT_SECRET"
```

---

## Update data.sql

All secrets are now available as shell variables. Run the command below to generate and update `data.sql` in one step:

```bash
cat > src/main/resources/data.sql << EOF
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
    '$KC_B_BROKER_SECRET',
    'http://host.docker.internal:8082/realms/my-realm/protocol/openid-connect/token',
    'my-client',
    '$MY_CLIENT_SECRET',
    'http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/certs',
    15
);
EOF
echo "data.sql updated"
```

Verify:

```bash
cat src/main/resources/data.sql
```

---

## Build and start the service

The `data.sql` is mounted into the container at runtime - no image rebuild needed when secrets change.

```bash
docker compose -f docs/docker-compose.yml --profile service up -d --build token-exchange-service
```

Wait for the service to be ready:

```bash
# Wait for Token exchange service
until curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:8090/health | grep -q "200"; do
  echo "Waiting for Token exchange service..."; sleep 3
done && echo "Token exchange service ready"
```

---

## Token exchange test

```bash
# 1. Generate Token A (KC-01)
TOKEN_A=$(curl -s -X POST \
  'http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/token' \
  -d "client_id=client-app" \
  -d "grant_type=password" \
  -d "username=user1" \
  -d "password=123" \
  | jq -r .access_token) && \
echo "ISS: $(echo $TOKEN_A | cut -d. -f2 | base64 -d 2>/dev/null | jq -r .iss)"

# 2. Exchange Token A for Token B
curl -s -X POST 'http://localhost:8090/exchange' \
  -H "Authorization: Bearer $TOKEN_A" \
  | jq .

# 3. Decode Token B
curl -s -X POST 'http://localhost:8090/exchange' \
  -H "Authorization: Bearer $TOKEN_A" \
  | jq -r .access_token \
  | cut -d. -f2 | base64 -d 2>/dev/null | jq '{iss, preferred_username, azp, exp}'
```

---

## Teardown

```bash
docker compose -f docs/docker-compose.yml --profile service down
```
