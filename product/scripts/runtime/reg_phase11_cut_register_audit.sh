#!/usr/bin/env bash
# REC-05 — cut register final audit.
# Static-only: no running stack required.
# Fails immediately on any undocumented fake indicator found in production Kotlin source.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SRC_ROOT="${PRODUCT_ROOT}/apps"

fail() { echo "REC-05 FAIL: $*" >&2; exit 1; }
pass_check() { echo "  [ok] $*"; }

echo "=== REC-05 cut register audit ==="
echo "product root: ${PRODUCT_ROOT}"
echo ""

# ── 1. No undocumented fake indicators in production Kotlin ──────────────────

echo "-- 1. Scanning production Kotlin for undocumented fake indicators"

PROD_KOTLIN_DIRS=()
while IFS= read -r -d '' d; do
  PROD_KOTLIN_DIRS+=("$d")
done < <(find "${SRC_ROOT}" -type d -name "main" -path "*/kotlin/*" -print0 2>/dev/null)

if [ ${#PROD_KOTLIN_DIRS[@]} -eq 0 ]; then
  fail "no production Kotlin source directories found under ${SRC_ROOT}"
fi

todo_hits=$(grep -r --include="*.kt" -l "TODO\|FIXME\|HACK\|XXX" "${PROD_KOTLIN_DIRS[@]}" 2>/dev/null || true)
if [ -n "$todo_hits" ]; then
  fail "found TODO/FIXME/HACK/XXX in production Kotlin: ${todo_hits}"
fi
pass_check "no TODO/FIXME/HACK/XXX in production Kotlin"

fake_hits=$(grep -ri --include="*.kt" -l "\bfake\b\|\bstub\b\|\bmock\b" "${PROD_KOTLIN_DIRS[@]}" 2>/dev/null || true)
if [ -n "$fake_hits" ]; then
  fail "found fake/stub/mock in production Kotlin: ${fake_hits}"
fi
pass_check "no fake/stub/mock in production Kotlin"

echo ""

# ── 2. Sumsub credential guard is present (KYC-01 honest gap) ────────────────

echo "-- 2. Sumsub credential guard (KYC-01 honest gap)"

SUMSUB_PROPS="${SRC_ROOT}/platform/src/main/kotlin/com/minifin/platform/kyc/SumsubProperties.kt"
[ -f "${SUMSUB_PROPS}" ] || fail "SumsubProperties.kt not found at expected path"
grep -q "appToken" "${SUMSUB_PROPS}" || fail "SumsubProperties.kt missing appToken field"
grep -q "secretKey" "${SUMSUB_PROPS}" || fail "SumsubProperties.kt missing secretKey field"

SUMSUB_SUPPORT="${SRC_ROOT}/platform/src/main/kotlin/com/minifin/platform/kyc/SumsubSupport.kt"
[ -f "${SUMSUB_SUPPORT}" ] || fail "SumsubSupport.kt not found"
grep -q "sumsub_not_configured\|not_configured\|appToken.*blank\|appToken.*isEmpty\|appToken.*isBlank\|configured()" "${SUMSUB_SUPPORT}" \
  || fail "SumsubSupport.kt missing credential guard (no-credentials path)"
pass_check "SumsubProperties and SumsubSupport credential guard present — KYC-01 honest gap confirmed"

echo ""

# ── 3. OpenSanctions local mode defaults to disabled (real path is default) ──

echo "-- 3. OpenSanctions local mode default"

OPENSANCTIONS_PROPS="${SRC_ROOT}/platform/src/main/kotlin/com/minifin/platform/sanctions/OpenSanctionsProperties.kt"
[ -f "${OPENSANCTIONS_PROPS}" ] || fail "OpenSanctionsProperties.kt not found"
grep -q '"disabled"' "${OPENSANCTIONS_PROPS}" || fail "OpenSanctionsProperties.kt missing disabled default for localMode"

COMPOSE_FILE="${PRODUCT_ROOT}/deploy/docker-compose.yml"
[ -f "${COMPOSE_FILE}" ] || fail "docker-compose.yml not found"
grep -q 'OPENSANCTIONS_LOCAL_MODE.*disabled' "${COMPOSE_FILE}" \
  || fail "docker-compose.yml: OPENSANCTIONS_LOCAL_MODE default is not 'disabled'"
pass_check "OpenSanctions local mode defaults to 'disabled' — real HTTP path is the default"

echo ""

# ── 4. No Stripe account-creation client in production code (MRC-01 honest gap)

echo "-- 4. No Stripe account-creation client (MRC-01 honest gap)"

stripe_create_hits=$(grep -r --include="*.kt" -l "Account\.create\|AccountLink\.create\|StripeClient\|com\.stripe" "${PROD_KOTLIN_DIRS[@]}" 2>/dev/null || true)
if [ -n "$stripe_create_hits" ]; then
  fail "found Stripe SDK account-creation code in production Kotlin: ${stripe_create_hits}"
fi
pass_check "no Stripe account-creation SDK code in production Kotlin — MRC-01 honest gap confirmed"

echo ""

# ── 5. Retained phase 11 scripts all exist and are executable ─────────────────

echo "-- 5. Retained phase 11 scripts"

for script in \
  reg_phase11_reset_dev.sh \
  reg_phase11_seed_dev.sh \
  reg_phase11_demo_path.sh \
  reg_phase11_cut_register_audit.sh; do
  path="${SCRIPT_DIR}/${script}"
  [ -f "${path}" ]    || fail "missing retained script: ${script}"
  [ -x "${path}" ]    || fail "not executable: ${script}"
  bash -n "${path}"   || fail "syntax error in: ${script}"
  pass_check "${script} — exists, executable, syntax ok"
done

echo ""

# ── 6. Cut register table ─────────────────────────────────────────────────────

echo "=== Cut register — known honest placeholders ==="
echo ""
echo "VENDOR CREDENTIAL GAPS"
echo "  MRC-01   No Stripe Connect onboarding. No Account.create/AccountLink.create in production code."
echo "           stripe_account_links rows created only by retained runtime scripts."
echo "  KYC-01   Sumsub KYC start returns sumsub_not_configured when credentials absent."
echo "           No fake vendor success. Real API called only when both credentials configured."
echo ""
echo "LOCAL-MODE ADAPTERS (dev/test tooling, real path is the default)"
echo "  SNX      OpenSanctions OPENSANCTIONS_LOCAL_MODE defaults to 'disabled' (real HTTP calls)."
echo "           Local modes no_match/match/unavailable/timeout active only when env var explicitly set."
echo ""
echo "RUNTIME PROOF / SMOKE ENDPOINTS (service-auth protected, not on public routes)"
echo "  platform GET  /internal/runtime/kafka                      Phase 01 RUN-03 Kafka smoke"
echo "  platform POST /internal/ledger/runtime/accounts            Phase 03 LDG proof"
echo "  platform POST /internal/ledger/runtime/journals            Phase 03 LDG proof"
echo "  platform GET  /internal/ledger/runtime/accounts/{id}/balance  Phase 03 LDG proof"
echo "  platform GET  /internal/ledger/runtime/reconciliation      LDG-05 retained script (still used)"
echo ""
echo "LOCAL DEFAULT SECRETS (change-me / local-* values, all externalized via env vars)"
echo "  SERVICE_AUTH_SECRET                    local-service-secret"
echo "  VAULT_PAN_ENCRYPTION_KEY               local-vault-pan-key-change-me"
echo "  WEBHOOK_SIGNING_SECRET_ENCRYPTION_KEY  local-webhook-signing-secret-key-change-me"
echo "  STRIPE_WEBHOOK_SIGNING_SECRET          whsec_local_test_secret"
echo "  SUMSUB_WEBHOOK_SECRET                  local-sumsub-webhook-secret"
echo ""
echo "TEST BIN"
echo "  VAULT_TEST_BIN  400000 (real Visa range, environment-configurable)"
echo ""
echo "FRONTEND SCAFFOLDS (Phase 10 explicitly deferred throughout)"
echo "  spa-enduser    build-verified shell only — UI-02 not claimed"
echo "  spa-merchant   build-verified shell only — UI-03 not claimed"
echo "  spa-backoffice build-verified shell only — UI-04 not claimed"
echo ""

echo "REC-05 cut register audit pass"
