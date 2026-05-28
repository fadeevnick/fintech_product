import React from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  AppShell,
  AppSidebar,
  AppSidebarItem,
  Badge,
  type BadgeTone,
  Button,
  DataTable,
  EmptyState,
  Field,
  Input,
  ModeTabs,
  PageHeader,
  Panel,
  Select,
  StatCard,
  StatGrid,
  TextArea,
  Toolbar,
  surfaceLabels,
} from "@minifin/ui";
import {
  createPlatformApiClient,
  type MerchantMeResponse,
  PlatformApiError,
} from "@minifin/api-client";
import "@minifin/ui/styles.css";
import "./styles.css";

const api = createPlatformApiClient({ baseUrl: "" });

// ─── domain types ──────────────────────────────────────────────────────────

interface ApiKeySummary {
  apiKeyId: string;
  label: string;
  keyPrefix: string;
  fingerprint: string;
  status: string;
  createdAt: string;
  revokedAt?: string | null;
  lastUsedAt?: string | null;
}
interface CreateApiKeyResponse extends ApiKeySummary {
  key: string;
}
interface WebhookEndpointDto {
  id: string;
  url: string;
  enabledEvents: string[];
  status: string;
  description?: string | null;
  secretPrefix: string;
  signingSecret?: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
}
interface WebhookEventDto {
  id: string;
  type: string;
  status: string;
  createdAt: string;
  retryCount: number;
  maxAttempts: number;
  lastAttemptAt?: string | null;
  lastErrorMessage?: string | null;
  lastHttpStatus?: number | null;
  dlqAt?: string | null;
}
interface PaymentIntentDto {
  id: string;
  amount: string;
  currency: string;
  state: string;
  description?: string | null;
  createdAt: string;
  capturedAt?: string | null;
}
interface SettlementBatchDto {
  batchId: string;
  status: string;
  currency: string;
  itemCount: number;
  grossAmount: string;
  merchantNetAmount: string;
  settledAt: string;
  createdAt: string;
}
interface DisputeDto {
  id: string;
  paymentIntentId: string;
  amount: string;
  currency: string;
  reasonCode: string;
  narrative?: string | null;
  state: string;
  merchantResponseDeadline: string;
  createdAt: string;
}

// ─── helpers ───────────────────────────────────────────────────────────────

function piTone(state: string): BadgeTone {
  if (state === "SETTLED") return "success";
  if (state === "CAPTURED") return "info";
  if (state === "AUTHORIZED") return "accent";
  if (state === "FAILED") return "danger";
  if (state === "DISPUTED") return "warning";
  if (state === "REFUNDED" || state === "PARTIALLY_REFUNDED") return "info";
  return "neutral";
}

function evTone(status: string): BadgeTone {
  if (status === "DELIVERED") return "success";
  if (status === "DLQ") return "warning";
  if (status === "FAILED") return "danger";
  if (status === "PENDING") return "neutral";
  return "neutral";
}

function settlementTone(status: string): BadgeTone {
  if (status === "SETTLED") return "success";
  if (status === "FAILED") return "danger";
  if (status === "PROCESSING") return "warning";
  return "neutral";
}

function disputeTone(state: string): BadgeTone {
  if (state === "WON") return "success";
  if (state === "LOST" || state === "ACCEPTED") return "danger";
  if (state === "OPEN" || state === "EVIDENCE_DUE") return "warning";
  return "neutral";
}

const WEBHOOK_EVENT_TYPES = [
  "payment_intent.created",
  "payment_intent.succeeded",
  "payment_intent.failed",
  "payment_intent.refunded",
  "chargeback.opened",
  "chargeback.updated",
  "settlement.created",
  "payout.paid",
];

const ONBOARDING_STEPS = [
  { label: "Account created", doneStatuses: ["EMAIL_VERIFIED", "PENDING_KYB", "KYB_IN_REVIEW", "KYB_APPROVED", "ACTIVE"] },
  { label: "Email verified", doneStatuses: ["PENDING_KYB", "KYB_IN_REVIEW", "KYB_APPROVED", "ACTIVE"] },
  { label: "Business profile", doneStatuses: ["KYB_IN_REVIEW", "KYB_APPROVED", "ACTIVE"] },
  { label: "KYB under review", doneStatuses: ["KYB_APPROVED", "ACTIVE"] },
  { label: "KYB approved", doneStatuses: ["ACTIVE"] },
  { label: "Payments enabled", doneStatuses: ["ACTIVE"] },
  { label: "Settlements enabled", doneStatuses: ["ACTIVE"] },
];

function stepState(step: typeof ONBOARDING_STEPS[0], merchantStatus: string): "done" | "current" | "pending" {
  if (step.doneStatuses.includes(merchantStatus)) return "done";
  const prevIdx = ONBOARDING_STEPS.indexOf(step) - 1;
  if (prevIdx < 0) return "current";
  return ONBOARDING_STEPS[prevIdx].doneStatuses.includes(merchantStatus) ? "current" : "pending";
}

function fmtAmount(amount: string, currency: string): string {
  const n = parseFloat(amount);
  return `${currency} ${isNaN(n) ? amount : n.toFixed(2)}`;
}

const DISPUTE_ACTIONABLE_STATES = ["OPEN", "EVIDENCE_DUE"];

// ─── auth context ──────────────────────────────────────────────────────────

interface AuthCtx {
  user: MerchantMeResponse | null;
  refresh: () => Promise<void>;
  logout: () => Promise<void>;
}

const Ctx = React.createContext<AuthCtx>({ user: null, refresh: async () => {}, logout: async () => {} });
const useAuth = () => React.useContext(Ctx);

// ─── app root ──────────────────────────────────────────────────────────────

function App() {
  const [user, setUser] = React.useState<MerchantMeResponse | null>(null);
  const [ready, setReady] = React.useState(false);

  const refresh = React.useCallback(async () => {
    try { setUser(await api.merchantAuth.me()); } catch { setUser(null); }
  }, []);

  const logout = React.useCallback(async () => {
    try { await api.merchantAuth.logout(); } catch { /* ignore */ }
    setUser(null);
  }, []);

  React.useEffect(() => { refresh().finally(() => setReady(true)); }, [refresh]);

  if (!ready) return <div className="mch-splash">Loading…</div>;

  return (
    <Ctx.Provider value={{ user, refresh, logout }}>
      <BrowserRouter>
        <Routes>
          <Route element={<LoginPage />} path="/login" />
          <Route element={<RegisterPage />} path="/register" />
          <Route element={<VerifyEmailPage />} path="/verify-email" />
          <Route element={<GuardedLayout />} path="/*" />
        </Routes>
      </BrowserRouter>
    </Ctx.Provider>
  );
}

// ─── auth guard + layout ───────────────────────────────────────────────────

function GuardedLayout() {
  const { user } = useAuth();
  if (!user) return <Navigate replace to="/login" />;
  return <MerchantLayout />;
}

type RouteKey = "onboarding" | "api-keys" | "webhooks" | "payments" | "settlements" | "disputes";

const NAV: Array<{ key: RouteKey; label: string; section: string }> = [
  { key: "onboarding", label: "Onboarding", section: "Account" },
  { key: "api-keys", label: "API keys", section: "Integrations" },
  { key: "webhooks", label: "Webhooks", section: "Integrations" },
  { key: "payments", label: "Payments", section: "Business" },
  { key: "settlements", label: "Settlements", section: "Business" },
  { key: "disputes", label: "Disputes", section: "Business" },
];

function activeKey(pathname: string): RouteKey {
  const seg = pathname.split("/")[1];
  return (NAV.find((n) => n.key === seg)?.key ?? "onboarding") as RouteKey;
}

function MerchantLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const current = activeKey(location.pathname);
  const sections = [
    ...Array.from(new Set(NAV.map((n) => n.section))).map((section) => ({
      title: section,
      items: NAV.filter((n) => n.section === section).map((n) => (
        <AppSidebarItem key={n.key} active={n.key === current} label={n.label} onClick={() => navigate(`/${n.key}`)} />
      )),
    })),
    {
      title: "Session",
      items: [
        <AppSidebarItem
          key="signout"
          label="Sign out"
          onClick={async () => { await logout(); navigate("/login", { replace: true }); }}
        />,
      ],
    },
  ];
  return (
    <AppShell
      surface="merchant"
      sidebar={
        <AppSidebar
          sections={sections}
          surface="merchant"
          userMeta={user?.email}
          userName={user?.role ?? "Merchant"}
        />
      }
    >
      <Routes>
        <Route element={<OnboardingPage />} path="/onboarding" />
        <Route element={<ApiKeysPage />} path="/api-keys" />
        <Route element={<WebhooksPage />} path="/webhooks" />
        <Route element={<PaymentsPage />} path="/payments" />
        <Route element={<SettlementsPage />} path="/settlements" />
        <Route element={<DisputesPage />} path="/disputes" />
        <Route element={<Navigate replace to="/onboarding" />} path="*" />
      </Routes>
    </AppShell>
  );
}

// ─── auth pages ────────────────────────────────────────────────────────────

function LoginPage() {
  const { user, refresh } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  React.useEffect(() => { if (user) navigate("/onboarding", { replace: true }); }, [user, navigate]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.merchantAuth.login({ email, password });
      await refresh();
      navigate("/onboarding", { replace: true });
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "Sign in failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mch-auth-wrap">
      <PageHeader breadcrumbs={<span>Merchant / Sign in</span>} screenId="MDB-UI-01" subtitle="Sign in to your merchant account." title={surfaceLabels.merchant} />
      <Panel title="Sign in">
        <form className="mch-auth-form" onSubmit={submit}>
          <Field label="Email"><Input autoComplete="email" required type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
          <Field label="Password"><Input autoComplete="current-password" required type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
          {error && <div className="mch-error">{error}</div>}
          <div className="mch-row">
            <Button disabled={busy} type="submit" variant="primary">{busy ? "Signing in…" : "Sign in"}</Button>
            <Button type="button" variant="ghost" onClick={() => navigate("/register")}>Create account</Button>
          </div>
        </form>
      </Panel>
    </div>
  );
}

function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = React.useState({ email: "", password: "", companyName: "", country: "DE", businessType: "ONLINE_MARKETPLACE" });
  const [done, setDone] = React.useState<{ verificationToken?: string | null } | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  function set(field: string, value: string) { setForm((f) => ({ ...f, [field]: value })); }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await api.merchantAuth.register(form);
      setDone({ verificationToken: res.verificationToken });
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "Registration failed.");
    } finally {
      setBusy(false);
    }
  }

  if (done) return (
    <div className="mch-auth-wrap">
      <PageHeader breadcrumbs={<span>Merchant / Register</span>} title="Account created" />
      <Panel title="Check your email">
        <div className="mch-auth-form">
          <p className="mch-muted">We sent a verification link to <strong>{form.email}</strong>.</p>
          {done.verificationToken && (
            <div className="mch-token-box">
              <span className="mch-muted">Dev token:</span>
              <code className="mch-code">{done.verificationToken}</code>
              <Button size="sm" variant="ghost" onClick={() => navigate(`/verify-email?token=${done.verificationToken}`)}>Verify now</Button>
            </div>
          )}
          <Button variant="primary" onClick={() => navigate("/login")}>Back to sign in</Button>
        </div>
      </Panel>
    </div>
  );

  return (
    <div className="mch-auth-wrap">
      <PageHeader breadcrumbs={<span>Merchant / Register</span>} subtitle="Create a new merchant account." title="Create account" />
      <Panel title="Business details">
        <form className="mch-auth-form" onSubmit={submit}>
          <Field label="Company name"><Input required value={form.companyName} onChange={(e) => set("companyName", e.target.value)} /></Field>
          <Field label="Country">
            <Select value={form.country} onChange={(e) => set("country", e.target.value)}>
              <option value="DE">Germany</option><option value="FR">France</option><option value="NL">Netherlands</option>
              <option value="AT">Austria</option><option value="ES">Spain</option><option value="IT">Italy</option>
            </Select>
          </Field>
          <Field label="Business type">
            <Select value={form.businessType} onChange={(e) => set("businessType", e.target.value)}>
              <option value="ONLINE_MARKETPLACE">Online marketplace</option><option value="ECOMMERCE">E-commerce</option>
              <option value="SAAS">SaaS</option><option value="OTHER">Other</option>
            </Select>
          </Field>
          <Field label="Email"><Input autoComplete="email" required type="email" value={form.email} onChange={(e) => set("email", e.target.value)} /></Field>
          <Field label="Password"><Input autoComplete="new-password" required type="password" value={form.password} onChange={(e) => set("password", e.target.value)} /></Field>
          {error && <div className="mch-error">{error}</div>}
          <div className="mch-row">
            <Button disabled={busy} type="submit" variant="primary">{busy ? "Creating…" : "Create account"}</Button>
            <Button type="button" variant="ghost" onClick={() => navigate("/login")}>Back to sign in</Button>
          </div>
        </form>
      </Panel>
    </div>
  );
}

function VerifyEmailPage() {
  const navigate = useNavigate();
  const tokenFromUrl = new URLSearchParams(window.location.search).get("token") ?? "";
  const [token, setToken] = React.useState(tokenFromUrl);
  const [result, setResult] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.merchantAuth.verifyEmail({ token });
      setResult("Email verified. You can now sign in.");
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "Verification failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mch-auth-wrap">
      <PageHeader breadcrumbs={<span>Merchant / Verify email</span>} title="Verify your email" />
      <Panel title="Email verification">
        {result ? (
          <div className="mch-auth-form">
            <p className="mch-muted">{result}</p>
            <Button variant="primary" onClick={() => navigate("/login")}>Sign in</Button>
          </div>
        ) : (
          <form className="mch-auth-form" onSubmit={submit}>
            <Field label="Verification token" hint="Paste the token from the verification email."><Input required value={token} onChange={(e) => setToken(e.target.value)} /></Field>
            {error && <div className="mch-error">{error}</div>}
            <Button disabled={busy} type="submit" variant="primary">{busy ? "Verifying…" : "Verify email"}</Button>
          </form>
        )}
      </Panel>
    </div>
  );
}

// ─── onboarding ────────────────────────────────────────────────────────────

function OnboardingPage() {
  const { user } = useAuth();
  const merchantStatus = user?.merchantStatus ?? "UNKNOWN";

  return (
    <>
      <PageHeader breadcrumbs={<span>Merchant / Account / Onboarding</span>} rightSlot={<Badge tone="info">{merchantStatus}</Badge>} screenId="MDB-UI-02" subtitle="Track your progress to start accepting payments." title="Onboarding status" />
      <div className="mch-stack">
        <div className="mch-two-col">
          <Panel title="Onboarding progress">
            <div className="mch-steps">
              {ONBOARDING_STEPS.map((step, i) => {
                const state = stepState(step, merchantStatus);
                return (
                  <div key={i} className={`mch-step mch-step-${state}`}>
                    <span className="mch-step-idx">{state === "done" ? "✓" : i + 1}</span>
                    <span className="mch-step-label">{step.label}</span>
                    <Badge tone={state === "done" ? "success" : state === "current" ? "warning" : "neutral"}>
                      {state.toUpperCase()}
                    </Badge>
                  </div>
                );
              })}
            </div>
          </Panel>
          <Panel title="Account">
            <div className="mch-kv">
              <div><span>Merchant ID</span><strong className="mono">{user?.merchantId}</strong></div>
              <div><span>Employee ID</span><strong className="mono">{user?.employeeId}</strong></div>
              <div><span>Email</span><strong>{user?.email}</strong></div>
              <div><span>Role</span><strong>{user?.role}</strong></div>
              <div><span>Employee status</span><strong>{user?.employeeStatus}</strong></div>
            </div>
          </Panel>
        </div>
      </div>
    </>
  );
}

// ─── api keys ──────────────────────────────────────────────────────────────

function ApiKeysPage() {
  const [keys, setKeys] = React.useState<ApiKeySummary[]>([]);
  const [status, setStatus] = React.useState<"loading" | "ready" | "error">("loading");
  const [label, setLabel] = React.useState("");
  const [creating, setCreating] = React.useState(false);
  const [createError, setCreateError] = React.useState<string | null>(null);
  const [revealKey, setRevealKey] = React.useState<CreateApiKeyResponse | null>(null);
  const [revoking, setRevoking] = React.useState<string | null>(null);

  async function load() {
    setStatus("loading");
    try { setKeys(await api.request<ApiKeySummary[]>("/api/v1/merchant/api-keys")); setStatus("ready"); }
    catch { setStatus("error"); }
  }
  React.useEffect(() => { load(); }, []);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setCreateError(null);
    setCreating(true);
    try {
      const res = await api.request<CreateApiKeyResponse>("/api/v1/merchant/api-keys", {
        method: "POST",
        body: { label: label || `Key ${new Date().toISOString().slice(0, 10)}` },
        headers: { "Idempotency-Key": crypto.randomUUID() },
      });
      if (res.key !== "REDACTED_ON_REPLAY") setRevealKey(res);
      setLabel("");
      load();
    } catch (err) {
      setCreateError(err instanceof PlatformApiError ? err.message : "Failed to create key.");
    } finally {
      setCreating(false);
    }
  }

  async function handleRevoke(id: string) {
    setRevoking(id);
    try { await api.request(`/api/v1/merchant/api-keys/${id}/revoke`, { method: "POST" }); load(); }
    catch { /* ignore */ } finally { setRevoking(null); }
  }

  const active = keys.filter((k) => k.status === "ACTIVE");
  const revoked = keys.filter((k) => k.status !== "ACTIVE");

  return (
    <>
      <PageHeader breadcrumbs={<span>Merchant / Integrations / API keys</span>} rightSlot={<Badge tone="info">{active.length} active</Badge>} screenId="MDB-UI-03" subtitle="Manage API keys for server-to-server integration." title="API keys" />
      <div className="mch-stack">
        {revealKey && (
          <Panel actions={<Button size="sm" variant="ghost" onClick={() => setRevealKey(null)}>Dismiss</Button>} title="New API key — copy this now">
            <div className="mch-reveal">
              <p className="mch-reveal-warn">This key is shown only once. Store it securely.</p>
              <code className="mch-code">{revealKey.key}</code>
              <div className="mch-kv">
                <div><span>Label</span><strong>{revealKey.label}</strong></div>
                <div><span>Fingerprint</span><strong className="mono">{revealKey.fingerprint}</strong></div>
              </div>
            </div>
          </Panel>
        )}
        <Panel
          actions={
            <form className="mch-inline-form" onSubmit={handleCreate}>
              <Input placeholder="Key label (optional)" value={label} onChange={(e) => setLabel(e.target.value)} />
              <Button disabled={creating} type="submit" variant="primary">{creating ? "Creating…" : "Create key"}</Button>
            </form>
          }
          title="Active keys"
        >
          {createError && <div className="mch-error">{createError}</div>}
          {status === "loading" && <EmptyState body="Fetching your API keys." title="Loading…" />}
          {status === "error" && <EmptyState body="Could not fetch API keys." title="Failed to load" />}
          {status === "ready" && (
            <DataTable<ApiKeySummary>
              columns={[
                { key: "label", header: "Label", render: (r) => r.label },
                { key: "prefix", header: "Prefix", className: "mono", render: (r) => r.keyPrefix },
                { key: "fp", header: "Fingerprint", className: "mono", render: (r) => r.fingerprint.slice(0, 12) + "…" },
                { key: "status", header: "Status", render: (r) => <Badge tone={r.status === "ACTIVE" ? "success" : "neutral"}>{r.status}</Badge> },
                { key: "created", header: "Created", render: (r) => r.createdAt.slice(0, 10) },
                {
                  key: "act", header: "", render: (r) =>
                    r.status === "ACTIVE" ? (
                      <Button disabled={revoking === r.apiKeyId} size="sm" variant="danger" onClick={() => handleRevoke(r.apiKeyId)}>
                        {revoking === r.apiKeyId ? "…" : "Revoke"}
                      </Button>
                    ) : null,
                },
              ]}
              emptyState={<EmptyState body="Create your first API key above." title="No keys yet" />}
              rowKey={(r) => r.apiKeyId}
              rows={active}
            />
          )}
        </Panel>
        {revoked.length > 0 && (
          <Panel title="Revoked keys">
            <DataTable<ApiKeySummary>
              columns={[
                { key: "label", header: "Label", render: (r) => r.label },
                { key: "prefix", header: "Prefix", className: "mono", render: (r) => r.keyPrefix },
                { key: "status", header: "Status", render: (r) => <Badge tone="neutral">{r.status}</Badge> },
                { key: "revoked", header: "Revoked", render: (r) => r.revokedAt?.slice(0, 10) ?? "—" },
              ]}
              rowKey={(r) => r.apiKeyId}
              rows={revoked}
            />
          </Panel>
        )}
      </div>
    </>
  );
}

// ─── webhooks ──────────────────────────────────────────────────────────────

const EVENT_STATUS_OPTIONS = ["DELIVERED", "FAILED", "DLQ", "PENDING"] as const;
type EventStatus = typeof EVENT_STATUS_OPTIONS[number];

function WebhooksPage() {
  const [tab, setTab] = React.useState<"endpoints" | "events">("endpoints");
  const [endpoints, setEndpoints] = React.useState<WebhookEndpointDto[]>([]);
  const [events, setEvents] = React.useState<WebhookEventDto[]>([]);
  const [epStatus, setEpStatus] = React.useState<"loading" | "ready" | "error">("loading");
  const [evStatus, setEvStatus] = React.useState<"loading" | "ready" | "error">("loading");
  const [evStatusFilter, setEvStatusFilter] = React.useState<EventStatus>("DELIVERED");
  const [showCreate, setShowCreate] = React.useState(false);
  const [createUrl, setCreateUrl] = React.useState("");
  const [createDesc, setCreateDesc] = React.useState("");
  const [selectedEvents, setSelectedEvents] = React.useState<string[]>(WEBHOOK_EVENT_TYPES);
  const [creating, setCreating] = React.useState(false);
  const [createError, setCreateError] = React.useState<string | null>(null);
  const [revealSecret, setRevealSecret] = React.useState<string | null>(null);
  const [deleting, setDeleting] = React.useState<string | null>(null);
  const [replaying, setReplaying] = React.useState<string | null>(null);

  async function loadEndpoints() {
    setEpStatus("loading");
    try {
      setEndpoints(await api.request<WebhookEndpointDto[]>("/api/v1/merchant/webhook-endpoints"));
      setEpStatus("ready");
    } catch { setEpStatus("error"); }
  }

  async function loadEvents(statusFilter: EventStatus) {
    setEvStatus("loading");
    try {
      const res = await api.request<{ items: WebhookEventDto[] }>(`/api/v1/merchant/webhook-events?status=${statusFilter}&limit=25`);
      setEvents(res.items);
      setEvStatus("ready");
    } catch { setEvStatus("error"); }
  }

  React.useEffect(() => { loadEndpoints(); }, []);
  React.useEffect(() => { loadEvents(evStatusFilter); }, [evStatusFilter]);

  const loadStatus = epStatus;

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setCreateError(null);
    setCreating(true);
    try {
      const ep = await api.request<WebhookEndpointDto>("/api/v1/merchant/webhook-endpoints", {
        method: "POST",
        body: { url: createUrl, enabledEvents: selectedEvents, description: createDesc || undefined },
      });
      if (ep.signingSecret) setRevealSecret(ep.signingSecret);
      setShowCreate(false);
      setCreateUrl("");
      setCreateDesc("");
      loadEndpoints();
    } catch (err) {
      setCreateError(err instanceof PlatformApiError ? err.message : "Failed to create endpoint.");
    } finally { setCreating(false); }
  }

  async function toggleStatus(ep: WebhookEndpointDto) {
    try {
      await api.request(`/api/v1/merchant/webhook-endpoints/${ep.id}`, { method: "PUT", body: { status: ep.status === "active" ? "disabled" : "active" } });
      loadEndpoints();
    } catch { /* ignore */ }
  }

  async function handleDelete(id: string) {
    setDeleting(id);
    try { await api.request(`/api/v1/merchant/webhook-endpoints/${id}`, { method: "DELETE" }); loadEndpoints(); }
    catch { /* ignore */ } finally { setDeleting(null); }
  }

  async function handleReplay(id: string) {
    setReplaying(id);
    try { await api.request(`/api/v1/merchant/webhook-events/${id}/replay`, { method: "POST" }); loadEvents(evStatusFilter); }
    catch { /* ignore */ } finally { setReplaying(null); }
  }

  function toggleEvent(ev: string) {
    setSelectedEvents((prev) => prev.includes(ev) ? prev.filter((e) => e !== ev) : [...prev, ev]);
  }

  return (
    <>
      <PageHeader breadcrumbs={<span>Merchant / Integrations / Webhooks</span>} screenId="MDB-UI-04" subtitle="Manage webhook endpoints and monitor delivery." title="Webhooks">
        <ModeTabs activeKey={tab} tabs={[{ key: "endpoints", label: "Endpoints" }, { key: "events", label: "Event log" }]} onChange={(k) => setTab(k as "endpoints" | "events")} />
      </PageHeader>
      <div className="mch-stack">
        {revealSecret && (
          <Panel actions={<Button size="sm" variant="ghost" onClick={() => setRevealSecret(null)}>Dismiss</Button>} title="Signing secret — save this now">
            <div className="mch-reveal">
              <p className="mch-reveal-warn">Verify HMAC-SHA256 signatures with this secret. It is shown only once.</p>
              <code className="mch-code">{revealSecret}</code>
            </div>
          </Panel>
        )}
        {tab === "endpoints" && (
          <Panel
            actions={<Button size="sm" variant="primary" onClick={() => setShowCreate(!showCreate)}>{showCreate ? "Cancel" : "Add endpoint"}</Button>}
            title="Endpoints"
          >
            {showCreate && (
              <form className="mch-create-form" onSubmit={handleCreate}>
                <Field label="URL (HTTPS)"><Input required type="url" value={createUrl} onChange={(e) => setCreateUrl(e.target.value)} /></Field>
                <Field label="Description"><Input value={createDesc} onChange={(e) => setCreateDesc(e.target.value)} /></Field>
                <fieldset className="mch-events-fieldset">
                  <legend className="mch-muted">Events</legend>
                  <div className="mch-events-grid">
                    {WEBHOOK_EVENT_TYPES.map((ev) => (
                      <label key={ev} className="mch-check"><input checked={selectedEvents.includes(ev)} type="checkbox" onChange={() => toggleEvent(ev)} />{ev}</label>
                    ))}
                  </div>
                </fieldset>
                {createError && <div className="mch-error">{createError}</div>}
                <Button disabled={creating} type="submit" variant="primary">{creating ? "Creating…" : "Create endpoint"}</Button>
              </form>
            )}
            {loadStatus === "loading" && <EmptyState body="Fetching endpoints." title="Loading…" />}
            {loadStatus === "error" && <EmptyState body="Could not fetch webhook endpoints." title="Failed to load" />}
            {loadStatus === "ready" && (
              <DataTable<WebhookEndpointDto>
                columns={[
                  { key: "url", header: "URL", render: (r) => <span className="mono mch-url">{r.url}</span> },
                  { key: "events", header: "Events", render: (r) => `${r.enabledEvents.length}` },
                  { key: "status", header: "Status", render: (r) => <Badge tone={r.status === "active" ? "success" : "neutral"}>{r.status.toUpperCase()}</Badge> },
                  { key: "prefix", header: "Secret prefix", className: "mono", render: (r) => r.secretPrefix },
                  {
                    key: "act", header: "", render: (r) => (
                      <Toolbar>
                        <Button size="sm" variant="secondary" onClick={() => toggleStatus(r)}>{r.status === "active" ? "Disable" : "Enable"}</Button>
                        <Button disabled={deleting === r.id} size="sm" variant="danger" onClick={() => handleDelete(r.id)}>{deleting === r.id ? "…" : "Delete"}</Button>
                      </Toolbar>
                    ),
                  },
                ]}
                emptyState={<EmptyState body="Add a webhook endpoint to start receiving events." title="No endpoints" />}
                rowKey={(r) => r.id}
                rows={endpoints.filter((e) => !e.deletedAt)}
              />
            )}
          </Panel>
        )}
        {tab === "events" && (
          <Panel
            actions={
              <Select value={evStatusFilter} onChange={(e) => setEvStatusFilter(e.target.value as EventStatus)}>
                {EVENT_STATUS_OPTIONS.map((s) => <option key={s} value={s}>{s}</option>)}
              </Select>
            }
            title="Event log"
          >
            {evStatus === "loading" && <EmptyState body="Fetching events." title="Loading…" />}
            {evStatus === "error" && <EmptyState body="Could not fetch webhook events." title="Failed to load" />}
            {evStatus === "ready" && (
              <DataTable<WebhookEventDto>
                columns={[
                  { key: "type", header: "Type", render: (r) => r.type },
                  { key: "status", header: "Status", render: (r) => <Badge tone={evTone(r.status)}>{r.status}</Badge> },
                  { key: "tries", header: "Attempts", render: (r) => `${r.retryCount} / ${r.maxAttempts}` },
                  { key: "last", header: "Last attempt", render: (r) => r.lastAttemptAt?.slice(0, 19).replace("T", " ") ?? "—" },
                  { key: "err", header: "Error", render: (r) => r.lastErrorMessage ? <span className="mch-muted">{r.lastErrorMessage.slice(0, 60)}{r.lastErrorMessage.length > 60 ? "…" : ""}</span> : "—" },
                  {
                    key: "act", header: "", render: (r) => (
                      <Button disabled={replaying === r.id} size="sm" variant="ghost" onClick={() => handleReplay(r.id)}>{replaying === r.id ? "…" : "Replay"}</Button>
                    ),
                  },
                ]}
                emptyState={<EmptyState body={`No ${evStatusFilter.toLowerCase()} events.`} title="No events" />}
                rowKey={(r) => r.id}
                rows={events}
              />
            )}
          </Panel>
        )}
      </div>
    </>
  );
}

// ─── payments ──────────────────────────────────────────────────────────────

function PaymentsPage() {
  const [payments, setPayments] = React.useState<PaymentIntentDto[]>([]);
  const [loadStatus, setLoadStatus] = React.useState<"loading" | "ready" | "error">("loading");
  const [selected, setSelected] = React.useState<PaymentIntentDto | null>(null);

  React.useEffect(() => {
    api.request<{ items: PaymentIntentDto[] }>("/api/v1/merchant/payment-intents?limit=50")
      .then((res) => { setPayments(res.items); setLoadStatus("ready"); })
      .catch(() => setLoadStatus("error"));
  }, []);

  const counts = React.useMemo(() => {
    const c: Record<string, number> = {};
    for (const p of payments) c[p.state] = (c[p.state] ?? 0) + 1;
    return c;
  }, [payments]);

  return (
    <>
      <PageHeader breadcrumbs={<span>Merchant / Business / Payments</span>} screenId="MDB-UI-05" subtitle="Payment intents processed through your account." title="Payments">
        {loadStatus === "ready" && (
          <StatGrid columns={4}>
            <StatCard label="Total" value={payments.length} />
            <StatCard label="Settled" tone="success" value={counts["SETTLED"] ?? 0} />
            <StatCard label="Captured" tone="info" value={counts["CAPTURED"] ?? 0} />
            <StatCard label="Disputed" tone="warning" value={counts["DISPUTED"] ?? 0} />
          </StatGrid>
        )}
      </PageHeader>
      <div className="mch-stack">
        {selected && (
          <Panel actions={<Button size="sm" variant="ghost" onClick={() => setSelected(null)}>Close</Button>} title={`Payment intent ${selected.id.slice(0, 8)}…`}>
            <div className="mch-kv">
              <div><span>ID</span><strong className="mono">{selected.id}</strong></div>
              <div><span>Amount</span><strong>{fmtAmount(selected.amount, selected.currency)}</strong></div>
              <div><span>State</span><strong><Badge tone={piTone(selected.state)}>{selected.state}</Badge></strong></div>
              <div><span>Created</span><strong>{selected.createdAt.slice(0, 19).replace("T", " ")}</strong></div>
              {selected.capturedAt && <div><span>Captured</span><strong>{selected.capturedAt.slice(0, 19).replace("T", " ")}</strong></div>}
              {selected.description && <div><span>Description</span><strong>{selected.description}</strong></div>}
            </div>
          </Panel>
        )}
        <Panel title="Payment intents">
          {loadStatus === "loading" && <EmptyState body="Fetching payment intents." title="Loading…" />}
          {loadStatus === "error" && <EmptyState body="Could not fetch payment intents." title="Failed to load" />}
          {loadStatus === "ready" && (
            <DataTable<PaymentIntentDto>
              columns={[
                { key: "id", header: "ID", className: "mono", render: (r) => r.id.slice(0, 8) + "…" },
                { key: "amount", header: "Amount", render: (r) => fmtAmount(r.amount, r.currency) },
                { key: "state", header: "State", render: (r) => <Badge tone={piTone(r.state)}>{r.state}</Badge> },
                { key: "desc", header: "Description", render: (r) => r.description ?? "—" },
                { key: "created", header: "Created", render: (r) => r.createdAt.slice(0, 10) },
                { key: "act", header: "", render: (r) => <Button size="sm" variant="ghost" onClick={() => setSelected(r)}>View</Button> },
              ]}
              emptyState={<EmptyState body="Payment intents created via the API appear here." title="No payments yet" />}
              rowKey={(r) => r.id}
              rows={payments}
            />
          )}
        </Panel>
      </div>
    </>
  );
}

// ─── settlements ───────────────────────────────────────────────────────────

function SettlementsPage() {
  const [batches, setBatches] = React.useState<SettlementBatchDto[]>([]);
  const [loadStatus, setLoadStatus] = React.useState<"loading" | "ready" | "error">("loading");

  React.useEffect(() => {
    api.request<{ items: SettlementBatchDto[] }>("/api/v1/merchant/settlements?limit=25")
      .then((res) => { setBatches(res.items); setLoadStatus("ready"); })
      .catch(() => setLoadStatus("error"));
  }, []);

  const totalNet = React.useMemo(() =>
    batches.reduce((s, b) => s + parseFloat(b.merchantNetAmount || "0"), 0).toFixed(2),
  [batches]);

  return (
    <>
      <PageHeader breadcrumbs={<span>Merchant / Business / Settlements</span>} screenId="MDB-UI-06" subtitle="T+2 settlement batches and payout status." title="Settlements">
        {loadStatus === "ready" && (
          <StatGrid columns={3}>
            <StatCard label="Batches" value={batches.length} />
            <StatCard label="Net payout (EUR)" tone="success" value={totalNet} />
            <StatCard label="Settled" tone="info" value={batches.filter((b) => b.status === "SETTLED").length} />
          </StatGrid>
        )}
      </PageHeader>
      <div className="mch-stack">
        <Panel title="Settlement batches">
          {loadStatus === "loading" && <EmptyState body="Fetching settlement batches." title="Loading…" />}
          {loadStatus === "error" && <EmptyState body="Could not fetch settlements." title="Failed to load" />}
          {loadStatus === "ready" && (
            <DataTable<SettlementBatchDto>
              columns={[
                { key: "id", header: "Batch ID", className: "mono", render: (r) => r.batchId.slice(0, 8) + "…" },
                { key: "status", header: "Status", render: (r) => <Badge tone={settlementTone(r.status)}>{r.status}</Badge> },
                { key: "currency", header: "Currency", render: (r) => r.currency },
                { key: "items", header: "Payments", render: (r) => r.itemCount },
                { key: "gross", header: "Gross", render: (r) => fmtAmount(r.grossAmount, r.currency) },
                { key: "net", header: "Net payout", render: (r) => fmtAmount(r.merchantNetAmount, r.currency) },
                { key: "settled", header: "Settled at", render: (r) => r.settledAt.slice(0, 10) },
              ]}
              emptyState={<EmptyState body="Settlement batches appear here once captured payments are processed. Settlements run on a T+2 schedule." title="No settlements yet" />}
              rowKey={(r) => r.batchId}
              rows={batches}
            />
          )}
        </Panel>
      </div>
    </>
  );
}

// ─── disputes ──────────────────────────────────────────────────────────────

type DisputeStateFilter = "active" | "all" | "won" | "closed";

function filterDisputes(disputes: DisputeDto[], filter: DisputeStateFilter): DisputeDto[] {
  switch (filter) {
    case "active": return disputes.filter((d) => DISPUTE_ACTIONABLE_STATES.includes(d.state));
    case "won": return disputes.filter((d) => d.state === "WON");
    case "closed": return disputes.filter((d) => d.state === "LOST" || d.state === "ACCEPTED");
    default: return disputes;
  }
}

function DisputesPage() {
  const [disputes, setDisputes] = React.useState<DisputeDto[]>([]);
  const [loadStatus, setLoadStatus] = React.useState<"loading" | "ready" | "error">("loading");
  const [stateFilter, setStateFilter] = React.useState<DisputeStateFilter>("active");
  const [selected, setSelected] = React.useState<DisputeDto | null>(null);
  const [evidenceNarrative, setEvidenceNarrative] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);
  const [submitError, setSubmitError] = React.useState<string | null>(null);
  const [accepting, setAccepting] = React.useState(false);
  const [actionDone, setActionDone] = React.useState<string | null>(null);

  async function load() {
    setLoadStatus("loading");
    try {
      const res = await api.request<{ items: DisputeDto[] }>("/api/v1/merchant/disputes?limit=25");
      setDisputes(res.items);
      setLoadStatus("ready");
    } catch { setLoadStatus("error"); }
  }

  React.useEffect(() => { load(); }, []);

  React.useEffect(() => {
    if (selected && !filterDisputes(disputes, stateFilter).find((d) => d.id === selected.id)) {
      setSelected(null);
    }
  }, [stateFilter, disputes]);

  const visible = React.useMemo(() => filterDisputes(disputes, stateFilter), [disputes, stateFilter]);
  const isActionable = selected ? DISPUTE_ACTIONABLE_STATES.includes(selected.state) : false;

  async function submitEvidence(e: React.FormEvent) {
    e.preventDefault();
    if (!selected) return;
    setSubmitError(null);
    setSubmitting(true);
    try {
      await api.request(`/api/v1/merchant/disputes/${selected.id}/evidence`, {
        method: "POST",
        body: { narrative: evidenceNarrative, attachments: [] },
      });
      setActionDone("Evidence submitted.");
      setSelected(null);
      setEvidenceNarrative("");
      load();
    } catch (err) {
      setSubmitError(err instanceof PlatformApiError ? err.message : "Evidence submission failed.");
    } finally { setSubmitting(false); }
  }

  async function acceptDispute() {
    if (!selected) return;
    setAccepting(true);
    try {
      await api.request(`/api/v1/merchant/disputes/${selected.id}/accept`, { method: "POST" });
      setActionDone("Dispute accepted.");
      setSelected(null);
      load();
    } catch (err) {
      setSubmitError(err instanceof PlatformApiError ? err.message : "Failed to accept dispute.");
    } finally { setAccepting(false); }
  }

  return (
    <>
      <PageHeader breadcrumbs={<span>Merchant / Business / Disputes</span>} screenId="MDB-UI-07" subtitle="Chargeback disputes and evidence submission." title="Disputes">
        {loadStatus === "ready" && (
          <StatGrid columns={3}>
            <StatCard label="Total" value={disputes.length} />
            <StatCard label="Needs action" tone="warning" value={disputes.filter((d) => DISPUTE_ACTIONABLE_STATES.includes(d.state)).length} />
            <StatCard label="Won" tone="success" value={disputes.filter((d) => d.state === "WON").length} />
          </StatGrid>
        )}
      </PageHeader>
      <div className="mch-stack">
        {actionDone && (
          <Panel actions={<Button size="sm" variant="ghost" onClick={() => setActionDone(null)}>Dismiss</Button>} title="Action completed">
            <p className="mch-muted">{actionDone}</p>
          </Panel>
        )}
        {selected && (
          <Panel
            actions={
              isActionable ? (
                <Toolbar>
                  <Button disabled={accepting} size="sm" variant="danger" onClick={acceptDispute}>{accepting ? "…" : "Accept dispute"}</Button>
                  <Button size="sm" variant="ghost" onClick={() => { setSelected(null); setSubmitError(null); }}>Close</Button>
                </Toolbar>
              ) : (
                <Button size="sm" variant="ghost" onClick={() => { setSelected(null); setSubmitError(null); }}>Close</Button>
              )
            }
            title={`Dispute ${selected.id.slice(0, 8)}…`}
          >
            <div className="mch-kv">
              <div><span>Dispute ID</span><strong className="mono">{selected.id}</strong></div>
              <div><span>Payment intent</span><strong className="mono">{selected.paymentIntentId.slice(0, 8)}…</strong></div>
              <div><span>Amount</span><strong>{fmtAmount(selected.amount, selected.currency)}</strong></div>
              <div><span>State</span><strong><Badge tone={disputeTone(selected.state)}>{selected.state}</Badge></strong></div>
              <div><span>Reason</span><strong>{selected.reasonCode}</strong></div>
              {selected.narrative && <div><span>Narrative</span><strong>{selected.narrative}</strong></div>}
              <div><span>Deadline</span><strong>{selected.merchantResponseDeadline.slice(0, 10)}</strong></div>
            </div>
            {isActionable ? (
              <form className="mch-create-form" onSubmit={submitEvidence} style={{ marginTop: 16 }}>
                <Field label="Evidence narrative" hint="Describe why this charge is valid.">
                  <TextArea required rows={4} value={evidenceNarrative} onChange={(e) => setEvidenceNarrative(e.target.value)} />
                </Field>
                {submitError && <div className="mch-error">{submitError}</div>}
                <Button disabled={submitting || !evidenceNarrative} type="submit" variant="primary">{submitting ? "Submitting…" : "Submit evidence"}</Button>
              </form>
            ) : (
              <p className="mch-muted" style={{ marginTop: 12 }}>This dispute is in a terminal state. No further action is required.</p>
            )}
          </Panel>
        )}
        <Panel
          actions={
            <Select value={stateFilter} onChange={(e) => setStateFilter(e.target.value as DisputeStateFilter)}>
              <option value="active">Needs action</option>
              <option value="all">All</option>
              <option value="won">Won</option>
              <option value="closed">Lost / Accepted</option>
            </Select>
          }
          title="Disputes"
        >
          {loadStatus === "loading" && <EmptyState body="Fetching disputes." title="Loading…" />}
          {loadStatus === "error" && <EmptyState body="Could not fetch disputes." title="Failed to load" />}
          {loadStatus === "ready" && (
            <DataTable<DisputeDto>
              columns={[
                { key: "id", header: "ID", className: "mono", render: (r) => r.id.slice(0, 8) + "…" },
                { key: "pi", header: "Payment", className: "mono", render: (r) => r.paymentIntentId.slice(0, 8) + "…" },
                { key: "amount", header: "Amount", render: (r) => fmtAmount(r.amount, r.currency) },
                { key: "state", header: "State", render: (r) => <Badge tone={disputeTone(r.state)}>{r.state}</Badge> },
                { key: "reason", header: "Reason", render: (r) => r.reasonCode },
                { key: "deadline", header: "Deadline", render: (r) => r.merchantResponseDeadline.slice(0, 10) },
                { key: "act", header: "", render: (r) => <Button size="sm" variant="ghost" onClick={() => { setSelected(r); setSubmitError(null); setEvidenceNarrative(""); }}>View</Button> },
              ]}
              emptyState={<EmptyState body={stateFilter === "active" ? "No disputes require action." : "No disputes in this view."} title="No disputes" />}
              rowKey={(r) => r.id}
              rows={visible}
            />
          )}
        </Panel>
      </div>
    </>
  );
}

// ─── render ────────────────────────────────────────────────────────────────

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
