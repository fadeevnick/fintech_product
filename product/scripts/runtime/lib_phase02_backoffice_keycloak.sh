#!/usr/bin/env bash

keycloak_url="${KEYCLOAK_BASE_URL:-http://localhost:18080}"
realm="${BACKOFFICE_REALM:-minifin-backoffice}"
client_id="${BACKOFFICE_CLIENT_ID:-minifin-backoffice-local}"
operator_email="${BACKOFFICE_OPERATOR_EMAIL:-operator@minifin.local}"
compliance_email="${BACKOFFICE_COMPLIANCE_EMAIL:-compliance@minifin.local}"
norole_email="${BACKOFFICE_NOROLE_EMAIL:-viewer@minifin.local}"
backoffice_password="${BACKOFFICE_PASSWORD:-password123}"

kc_admin_token() {
  curl -fsS -X POST "${keycloak_url}/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=admin-cli" \
    -d "username=${KEYCLOAK_ADMIN_USER:-admin}" \
    -d "password=${KEYCLOAK_ADMIN_PASSWORD:-admin}" \
    -d "grant_type=password" \
    | node -e "let s='';process.stdin.on('data',d=>s+=d);process.stdin.on('end',()=>{const j=JSON.parse(s); if(!j.access_token) process.exit(1); console.log(j.access_token);})"
}

kc_json_array_length() {
  node -e "const fs=require('fs'); const j=JSON.parse(fs.readFileSync(process.argv[1],'utf8')); console.log(Array.isArray(j) ? j.length : 0);" "$1"
}

kc_json_first_id() {
  node -e "const fs=require('fs'); const j=JSON.parse(fs.readFileSync(process.argv[1],'utf8')); if(!Array.isArray(j) || !j[0]?.id) process.exit(1); console.log(j[0].id);" "$1"
}

kc_ensure_role() {
  local token="$1"
  local role="$2"
  local body="/tmp/minifin-kc-role-${role}.json"
  local status
  status="$(curl -sS -o "${body}" -w "%{http_code}" -X POST "${keycloak_url}/admin/realms/${realm}/roles" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${role}\"}")"
  test "${status}" = "201" -o "${status}" = "409"
}

kc_ensure_user() {
  local token="$1"
  local email="$2"
  local username="$3"
  local body="/tmp/minifin-kc-user-${username}.json"
  local status

  curl -fsS "${keycloak_url}/admin/realms/${realm}/users?username=${username}&exact=true" \
    -H "Authorization: Bearer ${token}" >"${body}"

  if test "$(kc_json_array_length "${body}")" = "0"; then
    status="$(curl -sS -o /tmp/minifin-kc-user-create.json -w "%{http_code}" -X POST "${keycloak_url}/admin/realms/${realm}/users" \
      -H "Authorization: Bearer ${token}" \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"${username}\",\"email\":\"${email}\",\"firstName\":\"${username}\",\"lastName\":\"Backoffice\",\"enabled\":true,\"emailVerified\":true,\"requiredActions\":[],\"credentials\":[{\"type\":\"password\",\"value\":\"${backoffice_password}\",\"temporary\":false}]}")"
    test "${status}" = "201" -o "${status}" = "409"
    curl -fsS "${keycloak_url}/admin/realms/${realm}/users?username=${username}&exact=true" \
      -H "Authorization: Bearer ${token}" >"${body}"
  fi

  local user_id
  user_id="$(kc_json_first_id "${body}")"
  curl -fsS -X PUT "${keycloak_url}/admin/realms/${realm}/users/${user_id}" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${username}\",\"email\":\"${email}\",\"firstName\":\"${username}\",\"lastName\":\"Backoffice\",\"enabled\":true,\"emailVerified\":true,\"requiredActions\":[]}" \
    >/tmp/minifin-kc-user-update.json
  curl -fsS -X PUT "${keycloak_url}/admin/realms/${realm}/users/${user_id}/reset-password" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"type\":\"password\",\"value\":\"${backoffice_password}\",\"temporary\":false}" \
    >/tmp/minifin-kc-user-reset-password.json
  echo "${user_id}"
}

kc_assign_realm_role() {
  local token="$1"
  local user_id="$2"
  local role="$3"
  local role_body="/tmp/minifin-kc-role-rep-${role}.json"

  curl -fsS "${keycloak_url}/admin/realms/${realm}/roles/${role}" \
    -H "Authorization: Bearer ${token}" >"${role_body}"

  curl -fsS -X POST "${keycloak_url}/admin/realms/${realm}/users/${user_id}/role-mappings/realm" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "[$(cat "${role_body}")]"
}

kc_seed_backoffice_realm() {
  local token
  local status
  local clients_body="/tmp/minifin-kc-clients.json"
  local client_uuid
  local operator_id
  local compliance_id
  local norole_id

  token="$(kc_admin_token)"

  status="$(curl -sS -o /tmp/minifin-kc-realm.json -w "%{http_code}" -X GET "${keycloak_url}/admin/realms/${realm}" \
    -H "Authorization: Bearer ${token}")"
  if test "${status}" = "404"; then
    curl -fsS -X POST "${keycloak_url}/admin/realms" \
      -H "Authorization: Bearer ${token}" \
      -H "Content-Type: application/json" \
      -d "{\"realm\":\"${realm}\",\"enabled\":true}" >/tmp/minifin-kc-realm-create.json
  else
    test "${status}" = "200"
  fi

  curl -fsS "${keycloak_url}/admin/realms/${realm}/clients?clientId=${client_id}" \
    -H "Authorization: Bearer ${token}" >"${clients_body}"
  if test "$(kc_json_array_length "${clients_body}")" = "0"; then
    curl -fsS -X POST "${keycloak_url}/admin/realms/${realm}/clients" \
      -H "Authorization: Bearer ${token}" \
      -H "Content-Type: application/json" \
      -d "{\"clientId\":\"${client_id}\",\"enabled\":true,\"publicClient\":true,\"directAccessGrantsEnabled\":true,\"standardFlowEnabled\":true,\"protocol\":\"openid-connect\"}" \
      >/tmp/minifin-kc-client-create.json
    curl -fsS "${keycloak_url}/admin/realms/${realm}/clients?clientId=${client_id}" \
      -H "Authorization: Bearer ${token}" >"${clients_body}"
  fi
  client_uuid="$(kc_json_first_id "${clients_body}")"
  curl -fsS -X PUT "${keycloak_url}/admin/realms/${realm}/clients/${client_uuid}" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"${client_uuid}\",\"clientId\":\"${client_id}\",\"enabled\":true,\"publicClient\":true,\"directAccessGrantsEnabled\":true,\"standardFlowEnabled\":true,\"protocol\":\"openid-connect\"}" \
    >/tmp/minifin-kc-client-update.json

  kc_ensure_role "${token}" "backoffice_operator"
  kc_ensure_role "${token}" "compliance_officer"
  kc_ensure_role "${token}" "senior_compliance"
  kc_ensure_role "${token}" "support_viewer"

  operator_id="$(kc_ensure_user "${token}" "${operator_email}" "operator")"
  compliance_id="$(kc_ensure_user "${token}" "${compliance_email}" "compliance")"
  norole_id="$(kc_ensure_user "${token}" "${norole_email}" "viewer")"

  kc_assign_realm_role "${token}" "${operator_id}" "backoffice_operator" >/tmp/minifin-kc-operator-role.json
  kc_assign_realm_role "${token}" "${compliance_id}" "compliance_officer" >/tmp/minifin-kc-compliance-role.json
  kc_assign_realm_role "${token}" "${norole_id}" "support_viewer" >/tmp/minifin-kc-viewer-role.json
}

kc_backoffice_token() {
  local username="$1"
  curl -fsS -X POST "${keycloak_url}/realms/${realm}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=${client_id}" \
    -d "username=${username}" \
    -d "password=${backoffice_password}" \
    -d "grant_type=password" \
    | node -e "let s='';process.stdin.on('data',d=>s+=d);process.stdin.on('end',()=>{const j=JSON.parse(s); if(!j.access_token) process.exit(1); console.log(j.access_token);})"
}
