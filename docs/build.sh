#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DATA_SQL="$PROJECT_ROOT/src/main/resources/data.sql"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
SERVICE_PORT=8090

KC01_URL="http://host.docker.internal:8081"
KC02_URL="http://localhost:8082"
KC02_INTERNAL_URL="http://host.docker.internal:8082"  # used inside the container
KC01_REALM="poc-realm"
KC02_REALM="my-realm"
SERVICE_URL="http://localhost:$SERVICE_PORT"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log()     { echo -e "${CYAN}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
step()    { echo -e "\n${BOLD}${CYAN}▶ $1${NC}"; }

error() {
  echo -e "${RED}[ERROR]${NC} $1"
  if docker compose -f "$COMPOSE_FILE" ps -q 2>/dev/null | grep -q .; then
    warn "Stopping all containers due to error..."
    docker compose -f "$COMPOSE_FILE" --profile service down 2>/dev/null || true
    success "Containers stopped."
  fi
  exit 1
}

ask() {
  local prompt="$1"
  local response
  echo -e "${YELLOW}$prompt [Y/n]:${NC} \c"
  read -r response </dev/tty
  [[ -z "$response" || "$response" =~ ^[Yy]$ ]]
}

# ─────────────────────────────────────────────
# Print remaining steps and exit
# ─────────────────────────────────────────────
show_next_steps() {
  local step_num=$1

  echo ""
  echo -e "${BOLD}${CYAN}═══════════════════════════════════════════${NC}"
  echo -e "${BOLD}  Next steps to complete manually${NC}"
  echo -e "${BOLD}${CYAN}═══════════════════════════════════════════${NC}"
  echo ""

  local n=1

  if [[ -n "${INSERT_SQL:-}" && "$step_num" -le 4 ]]; then
    echo -e "${BOLD}$n. Update data.sql${NC}"
    echo "   File: $DATA_SQL"
    echo ""
    echo "$INSERT_SQL"
    echo ""
    n=$((n+1))
  fi

  if [[ "$step_num" -le 5 ]]; then
    echo -e "${BOLD}$n. Build and start the service (Docker)${NC}"
    echo "   cd $PROJECT_ROOT"
    echo "   docker compose -f docs/docker-compose.yml --profile service up -d --build token-exchange-service"
    echo ""
    n=$((n+1))
  fi

  if [[ "$step_num" -le 6 ]]; then
    echo -e "${BOLD}$n. Generate Token A (KC-01)${NC}"
    echo "   TOKEN_A=\$(curl -s -X POST '$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/token' \\"
    echo "     -d 'client_id=client-app' \\"
    echo "     -d 'grant_type=password' \\"
    echo "     -d 'username=user1' \\"
    echo "     -d 'password=123' \\"
    echo "     | jq -r .access_token)"
    echo ""
    n=$((n+1))

    echo -e "${BOLD}$n. Exchange Token A for Token B${NC}"
    echo "   curl -s -X POST '$SERVICE_URL/exchange' \\"
    echo "     -H \"Authorization: Bearer \$TOKEN_A\" \\"
    echo "     | jq ."
    echo ""
    n=$((n+1))

    echo -e "${BOLD}$n. Decode Token B${NC}"
    echo "   curl -s -X POST '$SERVICE_URL/exchange' \\"
    echo "     -H \"Authorization: Bearer \$TOKEN_A\" \\"
    echo "     | jq -r .access_token \\"
    echo "     | cut -d. -f2 | base64 -d 2>/dev/null | jq '{iss, preferred_username, azp, exp}'"
    echo ""
  fi

  echo -e "${BOLD}${CYAN}═══════════════════════════════════════════${NC}"
  echo ""
  log "Service logs (when running): docker logs -f token-exchange-service"
  exit 0
}

kc_running() {
  docker compose -f "$COMPOSE_FILE" ps --status running 2>/dev/null \
    | grep -qE "keycloak-01|keycloak-02"
}

svc_container_running() {
  docker compose -f "$COMPOSE_FILE" --profile service ps --status running 2>/dev/null \
    | grep -q "token-exchange-service"
}

wait_for_keycloak() {
  local url="$1"
  local name="$2"
  local max=30
  local i=0
  log "Waiting for $name to be ready..."
  until curl -s -o /dev/null -w "%{http_code}" "$url/realms/master" 2>/dev/null | grep -q "200"; do
    sleep 3
    i=$((i + 1))
    [[ $i -ge $max ]] && error "$name did not start in time."
    echo -n "."
  done
  echo ""
  success "$name is ready."
}

wait_for_service() {
  local max=20
  local i=0
  log "Waiting for token-exchange-service to be ready..."
  until curl -s -o /dev/null -w "%{http_code}" "$SERVICE_URL/health" 2>/dev/null | grep -q "200"; do
    sleep 3
    i=$((i+1))
    if [[ $i -ge $max ]]; then
      warn "Service did not respond in time."
      log "Check logs: docker logs -f token-exchange-service"
      show_next_steps 6
    fi
    echo -n "."
  done
  echo ""
}

get_admin_token() {
  local url="$1"
  local token
  token=$(curl -s -X POST "$url/realms/master/protocol/openid-connect/token" \
    -d "client_id=admin-cli" \
    -d "grant_type=password" \
    -d "username=admin" \
    -d "password=admin" \
    | jq -r .access_token)
  [[ -z "$token" || "$token" == "null" ]] && error "Failed to get admin token from $url"
  echo "$token"
}

# Variables populated during execution - used by show_next_steps
INSERT_SQL=""

# ─────────────────────────────────────────────
# STEP 1 - Check and start Keycloaks
# ─────────────────────────────────────────────
step "STEP 1 - Keycloak instances"

if kc_running; then
  warn "Keycloak containers are already running."
  if ask "Stop and recreate them?"; then
    log "Stopping existing containers..."
    docker compose -f "$COMPOSE_FILE" --profile service down
    success "Containers stopped."
  else
    warn "Aborting: Keycloaks are already running and will not be recreated."
    warn "Stop them manually with: docker compose -f docs/docker-compose.yml down"
    exit 0
  fi
fi

log "Starting Keycloak instances..."
docker compose -f "$COMPOSE_FILE" up -d keycloak-01 keycloak-02 || error "Failed to start Keycloak containers."
wait_for_keycloak "$KC01_URL" "KC-01"
wait_for_keycloak "$KC02_URL" "KC-02"

# ─────────────────────────────────────────────
# STEP 2 - Configure KC-01
# ─────────────────────────────────────────────
step "STEP 2 - Configuring KC-01"
T1=$(get_admin_token "$KC01_URL")

curl -s -X POST \
  -H "Authorization: Bearer $T1" -H "Content-Type: application/json" \
  -d '{"realm":"'"$KC01_REALM"'","enabled":true}' \
  "$KC01_URL/admin/realms" > /dev/null || error "Failed to create realm on KC-01"
success "Realm $KC01_REALM created"

curl -s -X POST \
  -H "Authorization: Bearer $T1" -H "Content-Type: application/json" \
  -d '{"clientId":"client-app","enabled":true,"publicClient":true,"directAccessGrantsEnabled":true}' \
  "$KC01_URL/admin/realms/$KC01_REALM/clients" > /dev/null || error "Failed to create client-app"
success "client-app created"

curl -s -X POST \
  -H "Authorization: Bearer $T1" -H "Content-Type: application/json" \
  -d '{"clientId":"kc-b-broker","enabled":true,"publicClient":false,"clientAuthenticatorType":"client-secret","standardFlowEnabled":true,"redirectUris":["'"$KC02_URL"'/realms/'"$KC02_REALM"'/broker/kc-01/endpoint"]}' \
  "$KC01_URL/admin/realms/$KC01_REALM/clients" > /dev/null || error "Failed to create kc-b-broker"
success "kc-b-broker created"

USER_ID=$(curl -s -X POST \
  -H "Authorization: Bearer $T1" -H "Content-Type: application/json" \
  -d '{"username":"user1","email":"user@example.com","firstName":"User","lastName":"One","enabled":true,"emailVerified":true,"requiredActions":[]}' \
  -D - "$KC01_URL/admin/realms/$KC01_REALM/users" \
  | grep -i location | awk -F'/' '{print $NF}' | tr -d '\r')
[[ -z "$USER_ID" ]] && error "Failed to create user1"
success "User user1 created (id: $USER_ID)"

curl -s -X PUT \
  -H "Authorization: Bearer $T1" -H "Content-Type: application/json" \
  -d '{"type":"password","value":"123","temporary":false}' \
  "$KC01_URL/admin/realms/$KC01_REALM/users/$USER_ID/reset-password" > /dev/null || error "Failed to set password"
success "Password set"

KC_B_BROKER_ID=$(curl -s -H "Authorization: Bearer $T1" \
  "$KC01_URL/admin/realms/$KC01_REALM/clients?clientId=kc-b-broker" | jq -r '.[0].id')
[[ -z "$KC_B_BROKER_ID" || "$KC_B_BROKER_ID" == "null" ]] && error "Failed to get kc-b-broker id"

KC_B_BROKER_SECRET=$(curl -s -H "Authorization: Bearer $T1" \
  "$KC01_URL/admin/realms/$KC01_REALM/clients/$KC_B_BROKER_ID/client-secret" | jq -r '.value')
[[ -z "$KC_B_BROKER_SECRET" || "$KC_B_BROKER_SECRET" == "null" ]] && error "Failed to get kc-b-broker secret"
success "kc-b-broker secret obtained"

# ─────────────────────────────────────────────
# STEP 3 - Configure KC-02
# ─────────────────────────────────────────────
step "STEP 3 - Configuring KC-02"
T2=$(get_admin_token "$KC02_URL")

curl -s -X POST \
  -H "Authorization: Bearer $T2" -H "Content-Type: application/json" \
  -d '{"realm":"'"$KC02_REALM"'","enabled":true}' \
  "$KC02_URL/admin/realms" > /dev/null || error "Failed to create realm on KC-02"
success "Realm $KC02_REALM created"

curl -s -X POST \
  -H "Authorization: Bearer $T2" -H "Content-Type: application/json" \
  -d "{
    \"alias\":\"kc-01\",\"providerId\":\"oidc\",\"enabled\":true,
    \"config\":{
      \"clientId\":\"kc-b-broker\",
      \"clientSecret\":\"$KC_B_BROKER_SECRET\",
      \"clientAuthMethod\":\"client_secret_post\",
      \"issuer\":\"$KC01_URL/realms/$KC01_REALM\",
      \"authorizationUrl\":\"$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/auth\",
      \"tokenUrl\":\"$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/token\",
      \"userInfoUrl\":\"$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/userinfo\",
      \"jwksUrl\":\"$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/certs\",
      \"logoutUrl\":\"$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/logout\",
      \"tokenIntrospectionUrl\":\"$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/token/introspect\",
      \"metadataDescriptorUrl\":\"$KC01_URL/realms/$KC01_REALM/.well-known/openid-configuration\",
      \"validateSignature\":\"true\",\"useJwksUrl\":\"true\",\"pkceEnabled\":\"false\"
    }
  }" \
  "$KC02_URL/admin/realms/$KC02_REALM/identity-provider/instances" > /dev/null || error "Failed to create Identity Provider"
success "Identity Provider kc-01 created"

curl -s -X POST \
  -H "Authorization: Bearer $T2" -H "Content-Type: application/json" \
  -d '{"clientId":"my-client","enabled":true,"publicClient":false,"clientAuthenticatorType":"client-secret","serviceAccountsEnabled":true,"standardFlowEnabled":false,"directAccessGrantsEnabled":false}' \
  "$KC02_URL/admin/realms/$KC02_REALM/clients" > /dev/null || error "Failed to create my-client"
success "my-client created"

MY_CLIENT_ID=$(curl -s -H "Authorization: Bearer $T2" \
  "$KC02_URL/admin/realms/$KC02_REALM/clients?clientId=my-client" | jq -r '.[0].id')
[[ -z "$MY_CLIENT_ID" || "$MY_CLIENT_ID" == "null" ]] && error "Failed to get my-client id"

MY_CLIENT_SECRET=$(curl -s -H "Authorization: Bearer $T2" \
  "$KC02_URL/admin/realms/$KC02_REALM/clients/$MY_CLIENT_ID/client-secret" | jq -r '.value')
[[ -z "$MY_CLIENT_SECRET" || "$MY_CLIENT_SECRET" == "null" ]] && error "Failed to get my-client secret"
success "my-client secret obtained"

# ─────────────────────────────────────────────
# STEP 4 - Generate INSERT and update data.sql
# ─────────────────────────────────────────────
step "STEP 4 - data.sql"

INSERT_SQL="INSERT INTO tenant_config (
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
    '$KC01_URL/realms/$KC01_REALM',
    '$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/token/introspect',
    'kc-b-broker',
    '$KC_B_BROKER_SECRET',
    '$KC02_INTERNAL_URL/realms/$KC02_REALM/protocol/openid-connect/token',
    'my-client',
    '$MY_CLIENT_SECRET',
    '$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/certs',
    15
);"

echo ""
log "Generated INSERT:"
echo -e "${CYAN}─────────────────────────────────────────${NC}"
echo "$INSERT_SQL"
echo -e "${CYAN}─────────────────────────────────────────${NC}"
echo ""

if ask "Replace data.sql content with this INSERT?"; then
  echo "$INSERT_SQL" > "$DATA_SQL"
  success "data.sql updated: $DATA_SQL"
else
  warn "data.sql not updated."
  show_next_steps 4
fi

# ─────────────────────────────────────────────
# STEP 5 - Build Docker image and start service
# ─────────────────────────────────────────────
step "STEP 5 - Build Docker image and start service"
echo ""
log "data.sql will be mounted into the container at runtime - no rebuild needed when secrets change."
echo ""

SVC_STARTED=false

if ask "Build Docker image and start token-exchange-service?"; then

  if svc_container_running; then
    warn "token-exchange-service container is already running."
    if ask "Stop and recreate it?"; then
      docker compose -f "$COMPOSE_FILE" --profile service stop token-exchange-service
      docker compose -f "$COMPOSE_FILE" --profile service rm -f token-exchange-service
      success "Container removed."
    else
      warn "Container left running."
      show_next_steps 5
    fi
  fi

  log "Building Docker image (eclipse-temurin:25-jdk > eclipse-temurin:25-jre)..."
  docker compose -f "$COMPOSE_FILE" --profile service build token-exchange-service \
    || error "Docker build failed."
  success "Image built: token-exchange-service:local"

  log "Starting token-exchange-service container..."
  docker compose -f "$COMPOSE_FILE" --profile service up -d token-exchange-service \
    || error "Failed to start token-exchange-service container."

  wait_for_service
  success "Service is up at $SERVICE_URL"
  SVC_STARTED=true
else
  show_next_steps 5
fi

# ─────────────────────────────────────────────
# STEP 6 - Token exchange test (loop)
# ─────────────────────────────────────────────
run_exchange_test() {
  echo ""
  echo -e "${CYAN}─────────────────────────────────────────${NC}"
  log "Request: Generate Token A"
  echo -e "${CYAN}  curl -s -X POST '$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/token' \\"
  echo -e "    -d 'client_id=client-app' \\"
  echo -e "    -d 'grant_type=password' \\"
  echo -e "    -d 'username=user1' \\"
  echo -e "    -d 'password=123'${NC}"
  echo ""

  TOKEN_A=$(curl -s -X POST "$KC01_URL/realms/$KC01_REALM/protocol/openid-connect/token" \
    -d "client_id=client-app" \
    -d "grant_type=password" \
    -d "username=user1" \
    -d "password=123" \
    | jq -r .access_token)

  if [[ -z "$TOKEN_A" || "$TOKEN_A" == "null" ]]; then
    warn "Failed to generate Token A. Is KC-01 running?"
    return 1
  fi

  success "Token A generated"
  echo ""
  log "Token A decoded:"
  echo "$TOKEN_A" | cut -d. -f2 | base64 -d 2>/dev/null \
    | jq '{iss, preferred_username, azp, exp}' 2>/dev/null || true

  echo ""
  echo -e "${CYAN}─────────────────────────────────────────${NC}"
  log "Request: Exchange Token A > Token B"
  echo -e "${CYAN}  curl -s -X POST '$SERVICE_URL/exchange' \\"
  echo -e "    -H 'Authorization: Bearer <Token A>'${NC}"
  echo ""

  RESPONSE=$(curl -s -X POST "$SERVICE_URL/exchange" \
    -H "Authorization: Bearer $TOKEN_A")

  TOKEN_B=$(echo "$RESPONSE" | jq -r .access_token 2>/dev/null || true)

  if [[ -z "$TOKEN_B" || "$TOKEN_B" == "null" ]]; then
    warn "Token exchange failed. Response:"
    echo "$RESPONSE" | jq . 2>/dev/null || echo "$RESPONSE"
    warn "Check service logs: docker logs -f token-exchange-service"
    return 1
  fi

  success "Token B obtained"
  echo ""
  log "Token B decoded:"
  echo "$TOKEN_B" | cut -d. -f2 | base64 -d 2>/dev/null \
    | jq '{iss, preferred_username, azp, exp}' 2>/dev/null || true
  echo -e "${CYAN}─────────────────────────────────────────${NC}"
}

step "STEP 6 - Token exchange test"
echo ""

if ask "Execute token generation and exchange?"; then
  run_exchange_test || true

  while ask "Run the exchange again?"; do
    run_exchange_test || true
  done
else
  show_next_steps 6
fi

# ─────────────────────────────────────────────
# TEARDOWN
# ─────────────────────────────────────────────
echo ""
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════${NC}"
echo -e "${BOLD}  Teardown${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════${NC}"
echo ""

# Stop token-exchange-service container
if svc_container_running; then
  CMD="docker compose -f $COMPOSE_FILE --profile service stop token-exchange-service"
  if ask "Stop the token-exchange-service container?"; then
    log "Running: $CMD"
    eval "$CMD" && success "token-exchange-service stopped."
  else
    log "Container left running. To stop manually:"
    echo "  $CMD"
  fi
else
  log "token-exchange-service container is not running."
fi

echo ""

# Stop Keycloak containers
if kc_running; then
  CMD="docker compose -f $COMPOSE_FILE down"
  if ask "Stop Keycloak containers?"; then
    log "Running: $CMD"
    eval "$CMD" && success "Keycloak containers stopped."
  else
    log "Keycloak containers left running. To stop manually:"
    echo "  $CMD"
  fi
else
  log "Keycloak containers are not running."
fi

echo ""
success "All done."
log "Service logs: docker logs -f token-exchange-service"
