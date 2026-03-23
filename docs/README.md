# docs - Setup guide

This directory contains everything needed to set up and run the token exchange POC locally. Three approaches are available - choose the one that best fits your workflow.

---

## Files in this directory

| File | Description |
|------|-------------|
| `docker-compose.yml` | Spins up KC-01 (:8081) and KC-02 (:8082) |
| `build.sh` | Automated setup script - configures both Keycloaks, generates the DB insert, optionally builds and starts the service, and runs the exchange test |
| `tutorial-ui.md` | Step-by-step configuration via Keycloak Admin Console (browser) |
| `tutorial-cli.md` | Same configuration via `curl` commands in the terminal |

---

## Prerequisites - all approaches

- Docker and Docker Compose installed
- `host.docker.internal` resolving to `127.0.0.1` on your machine

  Add to `/etc/hosts` if not already present:
  ```
  127.0.0.1 host.docker.internal
  ```

- `curl` and `jq` installed

---

## Approach 1 - Automated script (`build.sh`)

**Best for:** running the full setup end-to-end without manual steps.

**Additional requirements:**
- `lsof` installed (used to detect port conflicts)

**What it does, step by step:**

1. Checks if Keycloak containers are already running - if so, asks whether to recreate them
2. Starts KC-01 and KC-02 via `docker-compose.yml` and waits for both to be ready
3. Configures KC-01: creates `poc-realm`, `client-app`, `kc-b-broker`, and `user1`
4. Configures KC-02: creates `my-realm`, the `kc-01` Identity Provider, and `my-client`
5. Generates the `INSERT INTO tenant_config` statement with all secrets filled in - asks whether to overwrite `data.sql`
6. Asks whether to build the Docker image and start the service container
   - Uses `eclipse-temurin:25-jdk` to build and `eclipse-temurin:25-jre` to run - no local Java needed
   - Mounts `data.sql` into the container at runtime - no image rebuild needed when secrets change
   - Detects if the container is already running and asks whether to recreate it
7. Runs the token exchange test - shows each curl command with real values before executing
   - Loops: keeps running the test until you answer `n`
8. Teardown: asks whether to stop the service container and the Keycloak containers separately - shows the command either way

**At any point, answering `n` stops the automation** and prints all remaining steps with the real values filled in, so you can continue manually.

**How to run:**

```bash
cd docs/
chmod +x build.sh
./build.sh
```

---

## Approach 2 - UI tutorial (`tutorial-ui.md`)

**Best for:** understanding each configuration step visually, or when you prefer to work through the Keycloak Admin Console.

**Additional requirements:**
- `git` installed (used to locate the project root)
- A browser

**What it covers:**

1. Starting the Keycloaks via `docker-compose.yml`
2. KC-01 configuration: realm, user, `client-app`, `kc-b-broker`
3. KC-02 configuration: realm, Identity Provider, `my-client`
4. Updating `data.sql` with the secrets copied from the UI
5. Optional: adding hardcoded claims per tenant via Protocol Mapper

**How to use:**

```bash
# Start the Keycloaks first
docker compose -f docs/docker-compose.yml up -d

# Then open the tutorial and follow each step in the browser
# KC-01: http://host.docker.internal:8081/admin
# KC-02: http://localhost:8082/admin
```

After completing the UI steps, update `src/main/resources/data.sql` with the secrets as shown in the **Update data.sql** section of the tutorial, then build and start the service:

```bash
docker compose -f docs/docker-compose.yml --profile service up -d --build token-exchange-service
```

---

## Approach 3 - CLI tutorial (`tutorial-cli.md`)

**Best for:** automating or scripting the setup yourself, or when you want full visibility into every API call being made.

**Additional requirements:**
- `curl` and `jq` installed
- `git` installed (used to locate the project root)

**What it covers:**

1. Starting the Keycloaks via `docker-compose.yml`
2. KC-01 configuration via Admin REST API: realm, `client-app`, `kc-b-broker`, user and password
3. KC-02 configuration via Admin REST API: realm, Identity Provider (using `$KC_B_BROKER_SECRET`), `my-client`
4. Updating `data.sql` directly from the terminal using shell variables - no manual copy-paste needed
5. Token exchange test: generate Token A, call `/exchange`, decode Token B

**How to use:**

```bash
# Start the Keycloaks first
docker compose -f docs/docker-compose.yml up -d

# Then run each block in tutorial-cli.md in order
# Variables like $KC_B_BROKER_SECRET and $MY_CLIENT_SECRET
# are set by earlier blocks and reused automatically
```

After running all blocks, build and start the service:

```bash
docker compose -f docs/docker-compose.yml --profile service up -d --build token-exchange-service
```

---

## Comparison

| | `build.sh` | UI tutorial | CLI tutorial |
|--|-----------|-------------|--------------|
| Manual steps required | None | Many | Few |
| Visibility into API calls | Low | None | High |
| Requires browser | No | Yes | No |
| Requires Java 25+ locally | No | No | No |
| Best for | Full automation | Learning / exploration | Scripting / debugging |
| `data.sql` updated automatically | Yes (asks) | Manual | Yes (one command) |

---

## Teardown

To stop everything:

```bash
# Stop all containers (Keycloaks + service)
docker compose -f docs/docker-compose.yml --profile service down

# Stop only the service container
docker compose -f docs/docker-compose.yml --profile service stop token-exchange-service

# Stop only the Keycloak containers
docker compose -f docs/docker-compose.yml down
```

---

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| `host.docker.internal` not resolving in browser | Missing `/etc/hosts` entry | Add `127.0.0.1 host.docker.internal` |
| `Account is not fully set up` on KC-26 | Missing `firstName`/`lastName` or `emailVerified` | Ensure user has all fields set |
| `I/O error on POST request for "http://localhost:8082"` | Service container uses `localhost` to reach KC-02 | Use `host.docker.internal:8082` in `internal_token_uri` in `data.sql` |
| `Key not found` during token exchange | KC-02 cannot fetch JWKS from KC-01 | Ensure `jwksUrl` uses `host.docker.internal`, not `localhost` |
| `invalid_token` on exchange | `iss` in token does not match `issuerUri` in `tenant_config` | Regenerate token - it may have been issued before KC-01 hostname was configured |
| `Standard token exchange is not enabled` | KC-26 V2 only supports internal-internal exchange | This POC uses the middleware approach - no native token exchange required |
| Service container not picking up new `data.sql` | Container needs restart to remount the file | `docker compose -f docs/docker-compose.yml --profile service restart token-exchange-service` |
