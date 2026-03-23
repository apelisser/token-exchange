# Keycloak configuration - UI tutorial

> **Prerequisites:**
> - Docker and Docker Compose installed
> - `curl` and `jq` installed (for the token exchange test)
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

---

## KC-01 - http://host.docker.internal:8081/admin

> Credentials: **admin / admin**

### 1. Create realm

- Click **Create realm**
- Name: `poc-realm`
- Enabled: ON
- **Create**

### 2. Create user

- Menu **Users** > **Add user**
- Username: `user1`
- Email: `user@example.com`
- First name: `User`
- Last name: `One`
- Email verified: ON
- **Create**

Go to **Credentials** tab:
- **Set password** > `123`
- Temporary: OFF
- **Save**

### 3. Create client `client-app`

- Menu **Clients** > **Create client**
- Client ID: `client-app`
- **Next**
- Client authentication: OFF
- Direct access grants: ON
- **Save**

### 4. Create client `kc-b-broker`

- Menu **Clients** > **Create client**
- Client ID: `kc-b-broker`
- **Next**
- Client authentication: ON
- Standard flow: ON
- Direct access grants: OFF
- **Next**
- Valid redirect URIs: `http://localhost:8082/realms/my-realm/broker/kc-01/endpoint`
- **Save**

Go to **Credentials** tab and copy the **Client secret** - it will be used in KC-02 and in `data.sql`.

---

## KC-02 - http://localhost:8082/admin

> Credentials: **admin / admin**

### 1. Create realm

- Click **Create realm**
- Name: `my-realm`
- Enabled: ON
- **Create**

### 2. Create Identity Provider

- Menu **Identity Providers** > **Add provider** > **OpenID Connect v1.0**
- Alias: `kc-01`
- Discovery endpoint: `http://host.docker.internal:8081/realms/poc-realm/.well-known/openid-configuration`
- Click **Import** - fills in the URLs automatically
- Client ID: `kc-b-broker`
- Client Secret: *(copied from KC-01)*
- **Add**

### 3. Create client `my-client`

- Menu **Clients** > **Create client**
- Client ID: `my-client`
- **Next**
- Client authentication: ON
- Service accounts roles: ON ← required for client_credentials
- Standard flow: OFF
- Direct access grants: OFF
- **Save**

Go to **Credentials** tab and copy the **Client secret**.

---

## Update data.sql

After completing the KC-01 and KC-02 configuration, update `src/main/resources/data.sql` with the secrets obtained above:

```sql
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
    '<SECRET_KC_B_BROKER>',           -- copied from KC-01 > Clients > kc-b-broker > Credentials
    'http://host.docker.internal:8082/realms/my-realm/protocol/openid-connect/token',
    'my-client',
    '<SECRET_MY_CLIENT>',             -- copied from KC-02 > Clients > my-client > Credentials
    'http://host.docker.internal:8081/realms/poc-realm/protocol/openid-connect/certs',
    15
);
```

> `jwks_uri` is set explicitly here - both Keycloaks run on the same machine using `host.docker.internal`, so discovery would resolve to `localhost` inside the container, which doesn't work.

---

## Build and start the service

After updating `data.sql`, build the Docker image and start the service. The `data.sql` file is mounted into the container at runtime - no image rebuild is needed when secrets change.

```bash
docker compose -f docs/docker-compose.yml --profile service up -d --build token-exchange-service
```

Wait for the service to be ready:

```bash
# Check health
curl -s -o /dev/null -w "%{http_code}" http://localhost:8090/exchange
# Expected: 401 (service is up, no token provided)

# Follow logs
docker logs -f token-exchange-service
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
  | jq -r .access_token) && echo "Token A: OK"

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

## Add custom claim (optional)

To add fixed claims per tenant in Token B:

- **Clients** > **my-client** > **Client scopes** > **my-client-dedicated**
- **Add mapper** > **By configuration** > **Hardcoded claim**
- Name: `tenant_name`
- Token Claim Name: `tenant_name`
- Claim value: `client-a`
- Add to access token: ON
- **Save**

After saving, restart the service container so it picks up the new claim:

```bash
docker compose -f docs/docker-compose.yml --profile service restart token-exchange-service
```

---

## Teardown

```bash
# Stop all containers
docker compose -f docs/docker-compose.yml --profile service down
```
