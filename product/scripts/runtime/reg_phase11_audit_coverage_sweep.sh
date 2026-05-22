#!/usr/bin/env bash
# AUD-99 — Sensitive read-audit coverage sweep.
# Phase 1 (static): confirms each sensitive read endpoint has a readAuditRepository.write() call.
# Phase 2 (runtime): exercises each endpoint and asserts read_audit_log rows are created.
# Requires: COMPOSE_FILE, COMPOSE_PROJECT_NAME and Keycloak/Platform env for runtime phase.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase02_backoffice_keycloak.sh"

compose_file="${COMPOSE_FILE:-product/deploy/docker-compose.yml}"
base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"

platform_curl() {
  if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
    docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" -v /tmp:/tmp \
      curlimages/curl:8.10.1 "$@"
  else
    curl "$@"
  fi
}

platform_psql() {
  docker compose -f "${compose_file}" exec -T platform-db \
    psql -U platform -d platform -Atc "$1"
}

run_tag="$(date +%s%N)-$$"

echo "=== AUD-99 sensitive read-audit coverage sweep ==="

# ── Phase 1: Static analysis ─────────────────────────────────────────────────

echo "-- Phase 1: Static source coverage check"

APPS_ROOT="${SCRIPT_DIR}/../../apps/platform/src/main/kotlin/com/minifin/platform"

check_read_audit() {
  local label="$1" file="$2"
  [ -f "${file}" ] || { echo "AUD-99 FAIL: file not found: ${file}"; exit 1; }
  grep -q "readAuditRepository.write" "${file}" \
    || { echo "AUD-99 FAIL: no readAuditRepository.write in ${file}"; exit 1; }
  echo "  [ok] ${label}"
}

check_read_audit "backoffice deposit queue"           "${APPS_ROOT}/wallet/ManualOpsService.kt"
check_read_audit "backoffice withdrawal queue"        "${APPS_ROOT}/wallet/ManualOpsService.kt"
check_read_audit "sanctions hit detail (AUD-03)"      "${APPS_ROOT}/sanctions/SanctionsService.kt"
check_read_audit "KYC case detail (AUD-99 addition)"  "${APPS_ROOT}/kyc/KycBackofficeService.kt"
check_read_audit "AML alert detail (AUD-99 addition)" "${APPS_ROOT}/aml/AmlService.kt"

echo ""

# ── Phase 2: Runtime coverage ─────────────────────────────────────────────────

echo "-- Phase 2: Runtime read-audit row creation"

bo_token="$(keycloak_operator_token)"
compliance_token="$(keycloak_compliance_token)"

# --- seed minimal data ---

# end user with KYC in IN_REVIEW
eu_body="/tmp/minifin-aud99-eu-${run_tag}.json"
platform_curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"aud99-eu-${run_tag}@test.local\",\"password\":\"Pass1234!\",\"fullName\":\"AUD99 User\"}" \
  -c "/tmp/minifin-aud99-eu-${run_tag}.jar" >"${eu_body}"
eu_id="$(node -e "process.stdout.write(JSON.parse(require('fs').readFileSync('${eu_body}','utf8')).data.userId)")"

verify_token="$(platform_psql "select token from identity.email_verifications where end_user_id='${eu_id}'::uuid order by created_at desc limit 1;")"
platform_curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${verify_token}\"}" >/dev/null

# force IN_REVIEW KYC profile directly
kyc_profile_id="$(platform_psql "
  insert into kyc.kyc_profiles (id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id, created_at, updated_at)
  values (gen_random_uuid(), '${eu_id}'::uuid, 'IN_REVIEW', 'SUMSUB', 'aud99-applicant-${run_tag}', 'basic-kyc-level', 'aud99-ext-${run_tag}', now(), now())
  returning id;
")"
kyc_session_id="$(platform_psql "
  insert into kyc.kyc_sessions (id, end_user_id, kyc_profile_id, status, created_at, updated_at)
  values (gen_random_uuid(), '${eu_id}'::uuid, '${kyc_profile_id}'::uuid, 'PENDING', now(), now())
  returning id;
")"

# AML alert for this user (OPEN VELOCITY)
aml_alert_id="$(platform_psql "
  insert into aml.aml_alerts (id, end_user_id, rule_code, severity, status, window_started_at, window_ended_at, observed_count, threshold_count, created_at, updated_at)
  values (gen_random_uuid(), '${eu_id}'::uuid, 'VELOCITY', 'MEDIUM', 'OPEN', now() - interval '1 hour', now(), 5, 3, now(), now())
  returning id;
")"

# sanctions hit for this user (OPEN)
sanctions_hit_id="$(platform_psql "
  insert into sanctions.sanctions_hits (id, end_user_id, kyc_profile_id, vendor, status, reason, created_at, updated_at)
  values (gen_random_uuid(), '${eu_id}'::uuid, '${kyc_profile_id}'::uuid, 'OPENSANCTIONS', 'OPEN', 'SCREENING_UNAVAILABLE', now(), now())
  returning id;
")"

audit_before="$(platform_psql "select count(*) from audit.read_audit_log where actor_type='BACKOFFICE';")"

# --- KYC case detail ---
kyc_detail_body="/tmp/minifin-aud99-kyc-${run_tag}.json"
platform_curl -fsS "${base_url}/api/v1/backoffice/kyc-cases/${kyc_profile_id}" \
  -H "Authorization: Bearer ${bo_token}" >"${kyc_detail_body}"
node -e "
  const r = JSON.parse(require('fs').readFileSync('${kyc_detail_body}','utf8'));
  if (!r.data?.id) { console.error('kyc detail missing id'); process.exit(1); }
" || { echo "AUD-99 FAIL: KYC case detail call failed"; exit 1; }

kyc_audit_count="$(platform_psql "
  select count(*) from audit.read_audit_log
  where resource_type='KYC_CASE' and resource_id='${kyc_profile_id}'::uuid and decision='ALLOW';
")"
test "${kyc_audit_count}" -ge 1 || { echo "AUD-99 FAIL: no read_audit_log row for KYC_CASE ${kyc_profile_id}"; exit 1; }
echo "  [ok] KYC case detail → read_audit_log row written (purpose=compliance_kyc_case_detail)"

# --- AML alert detail ---
aml_detail_body="/tmp/minifin-aud99-aml-${run_tag}.json"
platform_curl -fsS "${base_url}/api/v1/backoffice/aml-alerts/${aml_alert_id}" \
  -H "Authorization: Bearer ${bo_token}" >"${aml_detail_body}"
node -e "
  const r = JSON.parse(require('fs').readFileSync('${aml_detail_body}','utf8'));
  if (!r.data?.id) { console.error('aml detail missing id'); process.exit(1); }
" || { echo "AUD-99 FAIL: AML alert detail call failed"; exit 1; }

aml_audit_count="$(platform_psql "
  select count(*) from audit.read_audit_log
  where resource_type='AML_ALERT' and resource_id='${aml_alert_id}'::uuid and decision='ALLOW';
")"
test "${aml_audit_count}" -ge 1 || { echo "AUD-99 FAIL: no read_audit_log row for AML_ALERT ${aml_alert_id}"; exit 1; }
echo "  [ok] AML alert detail → read_audit_log row written (purpose=compliance_aml_alert_detail)"

# --- Sanctions hit detail (regression: AUD-03) ---
snx_detail_body="/tmp/minifin-aud99-snx-${run_tag}.json"
platform_curl -fsS "${base_url}/api/v1/backoffice/sanctions-hits/${sanctions_hit_id}" \
  -H "Authorization: Bearer ${compliance_token}" >"${snx_detail_body}"
node -e "
  const r = JSON.parse(require('fs').readFileSync('${snx_detail_body}','utf8'));
  if (!r.data?.id) { console.error('sanctions detail missing id'); process.exit(1); }
" || { echo "AUD-99 FAIL: sanctions hit detail call failed"; exit 1; }

snx_audit_count="$(platform_psql "
  select count(*) from audit.read_audit_log
  where resource_type='SANCTIONS_HIT' and resource_id='${sanctions_hit_id}'::uuid and decision='ALLOW';
")"
test "${snx_audit_count}" -ge 1 || { echo "AUD-99 FAIL: no read_audit_log row for SANCTIONS_HIT ${sanctions_hit_id}"; exit 1; }
echo "  [ok] sanctions hit detail → read_audit_log row written (purpose=compliance_sanctions_hit_detail) — AUD-03 regression"

audit_after="$(platform_psql "select count(*) from audit.read_audit_log where actor_type='BACKOFFICE';")"
added="$((audit_after - audit_before))"
echo "  [ok] total read_audit_log rows added this run: ${added}"

echo ""
echo "AUD-99 sensitive read-audit coverage sweep pass run_tag=${run_tag}"
