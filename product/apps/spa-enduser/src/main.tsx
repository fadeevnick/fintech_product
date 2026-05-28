import React from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
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
  PageHeader,
  Panel,
  Select,
  StatCard,
  StatGrid,
  Toolbar,
  surfaceLabels,
} from "@minifin/ui";
import {
  createPlatformApiClient,
  type MeResponse,
  PlatformApiError,
} from "@minifin/api-client";
import "@minifin/ui/styles.css";
import "./styles.css";

const api = createPlatformApiClient({ baseUrl: "" });

// ─── domain types ──────────────────────────────────────────────────────────

interface DepositResponse {
  depositId: string;
  userId: string;
  amount: string;
  currency: string;
  state: string;
  reason?: string | null;
  sourceOfFundsRequired: boolean;
  sourceOfFundsSubmitted: boolean;
  createdAt: string;
  decidedAt?: string | null;
}

interface WithdrawalResponse {
  withdrawalId: string;
  userId: string;
  amount: string;
  currency: string;
  state: string;
  reason?: string | null;
  createdAt: string;
  decidedAt?: string | null;
}

interface TransferResponse {
  transferId: string;
  senderUserId: string;
  receiverUserId: string;
  amount: string;
  currency: string;
  state: string;
  journalEntryId?: string | null;
  createdAt: string;
  completedAt?: string | null;
}

interface WalletSummaryResponse {
  walletId: string;
  ledgerAccountId: string;
  currency: string;
  balance: string;
  deposits: DepositResponse[];
  withdrawals: WithdrawalResponse[];
  transfers: TransferResponse[];
}

interface KycStartResponse {
  profileId: string;
  sessionId?: string | null;
  status: string;
  vendor: string;
  vendorApplicantId?: string | null;
  externalUserId: string;
  levelName?: string | null;
  accessToken?: string | null;
  configurationStatus: string;
}

interface CardResponse {
  id: string;
  state: string;
  last4: string;
  expirationMonth: number;
  expirationYear: number;
  bin: string;
}

// ─── activity feed ─────────────────────────────────────────────────────────

interface ActivityRow {
  id: string;
  kind: "Transfer" | "Deposit" | "Withdrawal";
  amount: string;
  currency: string;
  state: string;
  tone: BadgeTone;
  date: string;
  transferId?: string;
  sofRequired?: boolean;
  sofSubmitted?: boolean;
}

function buildActivity(wallet: WalletSummaryResponse): ActivityRow[] {
  const rows: ActivityRow[] = [
    ...wallet.transfers.map((t): ActivityRow => ({
      id: t.transferId,
      kind: "Transfer",
      amount: t.amount,
      currency: t.currency,
      state: t.state,
      tone: transferTone(t.state),
      date: t.createdAt,
      transferId: t.transferId,
    })),
    ...wallet.deposits.map((d): ActivityRow => ({
      id: d.depositId,
      kind: "Deposit",
      amount: d.amount,
      currency: d.currency,
      state: d.state,
      tone: depositTone(d.state),
      date: d.createdAt,
      sofRequired: d.sourceOfFundsRequired,
      sofSubmitted: d.sourceOfFundsSubmitted,
    })),
    ...wallet.withdrawals.map((w): ActivityRow => ({
      id: w.withdrawalId,
      kind: "Withdrawal",
      amount: w.amount,
      currency: w.currency,
      state: w.state,
      tone: depositTone(w.state),
      date: w.createdAt,
    })),
  ];
  return rows.sort((a, b) => b.date.localeCompare(a.date));
}

// ─── helpers ───────────────────────────────────────────────────────────────

function depositTone(state: string): BadgeTone {
  if (state === "COMPLETED") return "success";
  if (state === "REJECTED") return "danger";
  if (state === "REQUESTED" || state === "PENDING_OPERATOR_REVIEW") return "warning";
  return "neutral";
}

function transferTone(state: string): BadgeTone {
  if (state === "COMPLETED") return "success";
  if (state === "REJECTED" || state === "CANCELLED") return "danger";
  if (state.startsWith("HELD")) return "warning";
  return "neutral";
}

function kycTone(status: string): BadgeTone {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  if (status === "IN_REVIEW" || status === "PENDING") return "warning";
  return "neutral";
}

const SOF_CATEGORIES = ["SALARY", "SAVINGS", "BUSINESS_INCOME", "INVESTMENT", "GIFT", "OTHER"];

function fmtAmount(amount: string, currency: string): string {
  const n = parseFloat(amount);
  return `${currency} ${isNaN(n) ? amount : n.toFixed(2)}`;
}

// ─── auth context ──────────────────────────────────────────────────────────

interface AuthCtx {
  user: MeResponse | null;
  refresh: () => Promise<void>;
  logout: () => Promise<void>;
}

const Ctx = React.createContext<AuthCtx>({ user: null, refresh: async () => {}, logout: async () => {} });
const useAuth = () => React.useContext(Ctx);

// ─── app root ──────────────────────────────────────────────────────────────

function App() {
  const [user, setUser] = React.useState<MeResponse | null>(null);
  const [ready, setReady] = React.useState(false);

  const refresh = React.useCallback(async () => {
    try { setUser(await api.endUserAuth.me()); } catch { setUser(null); }
  }, []);

  const logout = React.useCallback(async () => {
    try { await api.endUserAuth.logout(); } catch { /* ignore */ }
    setUser(null);
  }, []);

  React.useEffect(() => { refresh().finally(() => setReady(true)); }, [refresh]);

  if (!ready) return <div className="eu-splash">Loading…</div>;

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
  const location = useLocation();
  if (!user) return <Navigate replace state={{ from: location.pathname }} to="/login" />;
  return <EnduserLayout />;
}

type RouteKey = "wallet" | "deposit" | "transfer" | "cards" | "kyc";

const NAV: Array<{ key: RouteKey; label: string; section: string }> = [
  { key: "wallet", label: "Wallet", section: "Money" },
  { key: "deposit", label: "Deposit", section: "Money" },
  { key: "transfer", label: "Transfer", section: "Money" },
  { key: "cards", label: "Cards", section: "Cards" },
  { key: "kyc", label: "Identity verification", section: "Account" },
];

function activeRouteKey(pathname: string): RouteKey {
  const seg = pathname.split("/")[1];
  return (NAV.find((n) => n.key === seg)?.key ?? "wallet") as RouteKey;
}

function EnduserLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const current = activeRouteKey(location.pathname);
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
      surface="enduser"
      sidebar={
        <AppSidebar
          sections={sections}
          surface="enduser"
          userMeta={user?.email}
          userName="My account"
        />
      }
    >
      <Routes>
        <Route element={<WalletPage />} path="/wallet" />
        <Route element={<DepositPage />} path="/deposit" />
        <Route element={<TransferPage />} path="/transfer" />
        <Route element={<CardsPage />} path="/cards" />
        <Route element={<KycPage />} path="/kyc" />
        <Route element={<TxDetailPage />} path="/tx/:id" />
        <Route element={<Navigate replace to="/wallet" />} path="*" />
      </Routes>
    </AppShell>
  );
}

// ─── auth pages ────────────────────────────────────────────────────────────

function LoginPage() {
  const { user, refresh } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTarget = (location.state as { from?: string } | null)?.from ?? "/wallet";
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  React.useEffect(() => { if (user) navigate(redirectTarget, { replace: true }); }, [user, navigate]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.endUserAuth.login({ email, password });
      await refresh();
      navigate(redirectTarget, { replace: true });
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "Sign in failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="eu-auth-wrap">
      <PageHeader subtitle="Sign in to your wallet." title={surfaceLabels.enduser} />
      <Panel title="Sign in">
        <form className="eu-form" onSubmit={submit}>
          <Field label="Email"><Input autoComplete="email" required type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
          <Field label="Password"><Input autoComplete="current-password" required type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
          {error && <div className="eu-error">{error}</div>}
          <div className="eu-row">
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
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [done, setDone] = React.useState<{ verificationToken?: string | null } | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await api.endUserAuth.register({ email, password });
      setDone({ verificationToken: res.verificationToken });
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "Registration failed.");
    } finally {
      setBusy(false);
    }
  }

  if (done) return (
    <div className="eu-auth-wrap">
      <PageHeader title="Account created" />
      <Panel title="Check your email">
        <div className="eu-form">
          <p className="eu-muted">We sent a verification link to <strong>{email}</strong>.</p>
          {done.verificationToken && (
            <div className="eu-token-box">
              <span className="eu-muted">Dev token:</span>
              <code className="eu-code">{done.verificationToken}</code>
              <Button size="sm" variant="ghost" onClick={() => navigate(`/verify-email?token=${done.verificationToken}`)}>Verify now</Button>
            </div>
          )}
          <Button variant="primary" onClick={() => navigate("/login")}>Back to sign in</Button>
        </div>
      </Panel>
    </div>
  );

  return (
    <div className="eu-auth-wrap">
      <PageHeader subtitle="Create a free wallet account." title="Create account" />
      <Panel title="Sign up">
        <form className="eu-form" onSubmit={submit}>
          <Field label="Email"><Input autoComplete="email" required type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
          <Field label="Password"><Input autoComplete="new-password" required type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
          {error && <div className="eu-error">{error}</div>}
          <div className="eu-row">
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

  async function verify(t: string) {
    setError(null);
    setBusy(true);
    try {
      await api.endUserAuth.verifyEmail({ token: t });
      setResult("Email verified. You can now sign in.");
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "Verification failed.");
    } finally {
      setBusy(false);
    }
  }

  React.useEffect(() => { if (tokenFromUrl) verify(tokenFromUrl); }, []);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    verify(token);
  }

  return (
    <div className="eu-auth-wrap">
      <PageHeader title="Verify your email" />
      <Panel title="Email verification">
        {result ? (
          <div className="eu-form">
            <p className="eu-muted">{result}</p>
            <Button variant="primary" onClick={() => navigate("/login")}>Sign in</Button>
          </div>
        ) : (
          <form className="eu-form" onSubmit={submit}>
            <Field label="Verification token" hint="Paste the token from the email."><Input required value={token} onChange={(e) => setToken(e.target.value)} /></Field>
            {error && <div className="eu-error">{error}</div>}
            <Button disabled={busy} type="submit" variant="primary">{busy ? "Verifying…" : "Verify email"}</Button>
          </form>
        )}
      </Panel>
    </div>
  );
}

// ─── wallet home ───────────────────────────────────────────────────────────

function WalletPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [wallet, setWallet] = React.useState<WalletSummaryResponse | null>(null);
  const [loadStatus, setLoadStatus] = React.useState<"loading" | "ready" | "error">("loading");

  function loadWallet() {
    setLoadStatus("loading");
    api.request<WalletSummaryResponse>("/api/v1/wallet")
      .then((w) => { setWallet(w); setLoadStatus("ready"); })
      .catch(() => setLoadStatus("error"));
  }

  React.useEffect(() => { loadWallet(); }, []);

  const activity = React.useMemo(() => wallet ? buildActivity(wallet) : [], [wallet]);
  const pendingDeposits = React.useMemo(
    () => wallet ? wallet.deposits.filter((d) => d.state === "REQUESTED" || d.state === "PENDING_OPERATOR_REVIEW").length : 0,
    [wallet],
  );
  const heldTransfers = React.useMemo(
    () => wallet ? wallet.transfers.filter((t) => t.state.startsWith("HELD")).length : 0,
    [wallet],
  );

  return (
    <>
      <PageHeader breadcrumbs={<span>Wallet / Home</span>} subtitle="Your balance and recent activity." title="Wallet">
        {loadStatus === "ready" && wallet && (
          <StatGrid columns={3}>
            <StatCard label="Balance" tone="success" value={fmtAmount(wallet.balance, wallet.currency)} />
            <StatCard label="Pending deposits" tone={pendingDeposits > 0 ? "warning" : "neutral"} value={pendingDeposits} />
            <StatCard label="Held transfers" tone={heldTransfers > 0 ? "warning" : "neutral"} value={heldTransfers} />
          </StatGrid>
        )}
      </PageHeader>
      <div className="eu-stack">
        {loadStatus === "loading" && <Panel title="Activity"><EmptyState body="Fetching wallet data." title="Loading…" /></Panel>}
        {loadStatus === "error" && <Panel title="Activity"><EmptyState body="Could not load wallet." title="Failed to load" /><div style={{ textAlign: "center", marginTop: 8 }}><Button size="sm" variant="secondary" onClick={loadWallet}>Retry</Button></div></Panel>}
        {loadStatus === "ready" && wallet && (
          <>
            <Panel title="Recent activity">
              <DataTable<ActivityRow>
                columns={[
                  { key: "kind", header: "Type", render: (r) => r.kind },
                  { key: "amount", header: "Amount", render: (r) => fmtAmount(r.amount, r.currency) },
                  {
                    key: "state",
                    header: "State",
                    render: (r) => (
                      <>
                        <Badge tone={r.tone}>{r.state}</Badge>
                        {r.sofRequired && !r.sofSubmitted && <> <Badge tone="warning">SoF required</Badge></>}
                      </>
                    ),
                  },
                  { key: "date", header: "Date", render: (r) => r.date.slice(0, 10) },
                  {
                    key: "act",
                    header: "",
                    render: (r) => r.transferId
                      ? <button className="eu-link" onClick={() => {
                          const tr = wallet!.transfers.find((t) => t.transferId === r.transferId);
                          navigate(`/tx/${r.transferId}`, { state: { transfer: tr } });
                        }}>View</button>
                      : null,
                  },
                ]}
                emptyState={<EmptyState body="No activity yet. Make a deposit to get started." title="No activity" />}
                rowKey={(r) => r.id}
                rows={activity}
              />
            </Panel>
            <Panel title="Your wallet address">
              <div className="eu-kv">
                <div><span>User ID</span><strong className="mono">{user?.userId}</strong></div>
              </div>
              <p className="eu-muted">Share this ID with another user so they can send you a transfer.</p>
            </Panel>
          </>
        )}
      </div>
    </>
  );
}

// ─── transaction detail ────────────────────────────────────────────────────

function TxDetailPage() {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();
  const stateTransfer = (location.state as { transfer?: TransferResponse } | null)?.transfer;
  const [transfer, setTransfer] = React.useState<TransferResponse | null>(stateTransfer ?? null);
  const [loadStatus, setLoadStatus] = React.useState<"loading" | "ready" | "error" | "not-found">(
    stateTransfer ? "ready" : "loading",
  );

  React.useEffect(() => {
    if (stateTransfer) return;
    api.request<WalletSummaryResponse>("/api/v1/wallet")
      .then((w) => {
        const found = w.transfers.find((t) => t.transferId === id);
        if (found) { setTransfer(found); setLoadStatus("ready"); }
        else setLoadStatus("not-found");
      })
      .catch(() => setLoadStatus("error"));
  }, [id, stateTransfer]);

  return (
    <>
      <PageHeader
        breadcrumbs={<span><button className="eu-link" onClick={() => navigate("/wallet")}>Wallet</button> / Transfer detail</span>}
        subtitle="Transfer transaction details."
        title="Transaction detail"
      />
      <div className="eu-stack">
        {loadStatus === "loading" && <Panel title="Loading…"><EmptyState body="Fetching transaction." title="Loading" /></Panel>}
        {loadStatus === "error" && <Panel title="Error"><EmptyState body="Could not load transaction." title="Failed to load" /></Panel>}
        {loadStatus === "not-found" && <Panel title="Not found"><EmptyState body="Transaction not found in your wallet." title="Not found" /></Panel>}
        {loadStatus === "ready" && transfer && (
          <Panel
            actions={<Button size="sm" variant="ghost" onClick={() => navigate("/wallet")}>Back to wallet</Button>}
            title={`Transfer ${transfer.transferId.slice(0, 8)}…`}
          >
            <div className="eu-kv">
              <div><span>Transfer ID</span><strong className="mono">{transfer.transferId}</strong></div>
              <div><span>Direction</span><strong>{transfer.senderUserId === user?.userId ? "Sent" : "Received"}</strong></div>
              <div><span>Amount</span><strong>{fmtAmount(transfer.amount, transfer.currency)}</strong></div>
              <div><span>State</span><strong><Badge tone={transferTone(transfer.state)}>{transfer.state}</Badge></strong></div>
              <div><span>From user</span><strong className="mono">{transfer.senderUserId}</strong></div>
              <div><span>To user</span><strong className="mono">{transfer.receiverUserId}</strong></div>
              {transfer.journalEntryId && <div><span>Journal</span><strong className="mono">{transfer.journalEntryId}</strong></div>}
              <div><span>Created</span><strong>{transfer.createdAt.slice(0, 19).replace("T", " ")}</strong></div>
              {transfer.completedAt && <div><span>Completed</span><strong>{transfer.completedAt.slice(0, 19).replace("T", " ")}</strong></div>}
            </div>
            {transfer.state.startsWith("HELD") && (
              <div className="eu-hold-notice">
                <strong>Transfer on hold</strong>
                <p className="eu-muted">This transfer is held pending recipient verification. It will be released automatically after 30 days if not completed sooner.</p>
              </div>
            )}
          </Panel>
        )}
      </div>
    </>
  );
}

// ─── deposit ───────────────────────────────────────────────────────────────

function DepositPage() {
  const [amount, setAmount] = React.useState("");
  const [pending, setPending] = React.useState<DepositResponse | null>(null);
  const [sofCategory, setSofCategory] = React.useState("SALARY");
  const [sofDesc, setSofDesc] = React.useState("");
  const [sofDone, setSofDone] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  async function submitDeposit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const dep = await api.request<DepositResponse>("/api/v1/deposits", {
        method: "POST",
        body: { amount, currency: "EUR" },
      });
      setPending(dep);
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "Deposit failed.");
    } finally {
      setBusy(false);
    }
  }

  async function submitSof(e: React.FormEvent) {
    e.preventDefault();
    if (!pending) return;
    setError(null);
    setBusy(true);
    try {
      await api.request(`/api/v1/deposits/${pending.depositId}/source-of-funds`, {
        method: "POST",
        body: { sourceCategory: sofCategory, description: sofDesc },
      });
      setSofDone(true);
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "Source-of-funds submission failed.");
    } finally {
      setBusy(false);
    }
  }

  if (sofDone) return (
    <>
      <PageHeader breadcrumbs={<span>Wallet / Deposit</span>} title="Deposit" />
      <Panel title="Source of funds submitted">
        <div className="eu-form">
          <p className="eu-muted">Your deposit is under review. Funds will be credited once approved.</p>
          <Button variant="primary" onClick={() => { setPending(null); setSofDone(false); setAmount(""); }}>New deposit</Button>
        </div>
      </Panel>
    </>
  );

  if (pending && !pending.sourceOfFundsRequired) return (
    <>
      <PageHeader breadcrumbs={<span>Wallet / Deposit</span>} title="Deposit" />
      <Panel title="Deposit submitted">
        <div className="eu-form">
          <div className="eu-kv">
            <div><span>Amount</span><strong>{fmtAmount(pending.amount, pending.currency)}</strong></div>
            <div><span>State</span><strong><Badge tone="warning">PENDING REVIEW</Badge></strong></div>
            <div><span>Deposit ID</span><strong className="mono">{pending.depositId}</strong></div>
          </div>
          <p className="eu-muted">Your deposit is pending operator review. Funds will be credited once approved.</p>
          <Button variant="primary" onClick={() => { setPending(null); setAmount(""); }}>New deposit</Button>
        </div>
      </Panel>
    </>
  );

  if (pending?.sourceOfFundsRequired && !pending.sourceOfFundsSubmitted) return (
    <>
      <PageHeader breadcrumbs={<span>Wallet / Deposit / Source of funds</span>} rightSlot={<Badge tone="warning">SoF required</Badge>} subtitle="Deposits over EUR 15,000 require a source of funds declaration." title="Source of funds" />
      <Panel title="Declare source of funds">
        <form className="eu-form" onSubmit={submitSof}>
          <div className="eu-kv">
            <div><span>Deposit amount</span><strong>{fmtAmount(pending.amount, pending.currency)}</strong></div>
            <div><span>Deposit ID</span><strong className="mono">{pending.depositId}</strong></div>
          </div>
          <Field label="Source category">
            <Select value={sofCategory} onChange={(e) => setSofCategory(e.target.value)}>
              {SOF_CATEGORIES.map((c) => <option key={c} value={c}>{c.replace("_", " ")}</option>)}
            </Select>
          </Field>
          <Field label="Description" hint="Describe where the funds come from.">
            <Input required value={sofDesc} onChange={(e) => setSofDesc(e.target.value)} />
          </Field>
          {error && <div className="eu-error">{error}</div>}
          <Button disabled={busy} type="submit" variant="primary">{busy ? "Submitting…" : "Submit declaration"}</Button>
        </form>
      </Panel>
    </>
  );

  return (
    <>
      <PageHeader breadcrumbs={<span>Wallet / Deposit</span>} subtitle="Request a deposit to your wallet. Amounts over EUR 15,000 require a source of funds declaration." title="Deposit" />
      <div className="eu-stack">
        <Panel title="Deposit request">
          <form className="eu-form" onSubmit={submitDeposit}>
            <Field label="Amount (EUR)" hint="Enter the amount you want to deposit.">
              <Input min="0.01" required step="0.01" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </Field>
            {error && <div className="eu-error">{error}</div>}
            <Button disabled={busy || !amount} type="submit" variant="primary">{busy ? "Creating…" : "Create deposit request"}</Button>
          </form>
        </Panel>
      </div>
    </>
  );
}

// ─── transfer ──────────────────────────────────────────────────────────────

function TransferPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [receiverUserId, setReceiverUserId] = React.useState("");
  const [amount, setAmount] = React.useState("");
  const [done, setDone] = React.useState<TransferResponse | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const transfer = await api.request<TransferResponse>("/api/v1/transfers", {
        method: "POST",
        body: { receiverUserId, amount, currency: "EUR" },
        headers: { "Idempotency-Key": crypto.randomUUID() },
      });
      setDone(transfer);
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "Transfer failed.");
    } finally {
      setBusy(false);
    }
  }

  if (done) return (
    <>
      <PageHeader breadcrumbs={<span>Wallet / Transfer</span>} title="Transfer" />
      <Panel title="Transfer submitted">
        <div className="eu-form">
          <div className="eu-kv">
            <div><span>Transfer ID</span><strong className="mono">{done.transferId}</strong></div>
            <div><span>Amount</span><strong>{fmtAmount(done.amount, done.currency)}</strong></div>
            <div><span>State</span><strong><Badge tone={transferTone(done.state)}>{done.state}</Badge></strong></div>
          </div>
          {done.state.startsWith("HELD") && (
            <div className="eu-hold-notice">
              <strong>Transfer held</strong>
              <p className="eu-muted">The recipient is not yet verified. The transfer is held and will be released once the recipient completes verification, or auto-released after 30 days.</p>
            </div>
          )}
          <div className="eu-row">
            <Button variant="primary" onClick={() => navigate(`/tx/${done.transferId}`)}>View details</Button>
            <Button variant="ghost" onClick={() => { setDone(null); setAmount(""); setReceiverUserId(""); }}>New transfer</Button>
          </div>
        </div>
      </Panel>
    </>
  );

  return (
    <>
      <PageHeader breadcrumbs={<span>Wallet / Transfer</span>} subtitle="Send money to another wallet user." title="Transfer" />
      <div className="eu-stack">
        <Panel title="New transfer">
          <form className="eu-form" onSubmit={submit}>
            <Field label="Recipient user ID" hint="Ask the recipient to share their user ID — it is shown on their Wallet page.">
              <Input required value={receiverUserId} onChange={(e) => setReceiverUserId(e.target.value)} />
            </Field>
            {user?.userId && (
              <p className="eu-muted">Your user ID: <span className="mono">{user.userId}</span></p>
            )}
            <Field label="Amount (EUR)">
              <Input min="0.01" required step="0.01" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </Field>
            {error && <div className="eu-error">{error}</div>}
            <Button disabled={busy || !receiverUserId || !amount} type="submit" variant="primary">{busy ? "Sending…" : "Send transfer"}</Button>
          </form>
        </Panel>
      </div>
    </>
  );
}

// ─── cards ─────────────────────────────────────────────────────────────────

function CardsPage() {
  const [cards, setCards] = React.useState<CardResponse[]>([]);
  const [listStatus, setListStatus] = React.useState<"loading" | "ready" | "error">("loading");
  const [issuedCard, setIssuedCard] = React.useState<CardResponse | null>(null);
  const [issuing, setIssuing] = React.useState(false);
  const [issueError, setIssueError] = React.useState<string | null>(null);

  async function loadCards() {
    setListStatus("loading");
    try {
      const res = await api.request<{ items: CardResponse[] }>("/api/v1/cards");
      setCards(res.items);
      setListStatus("ready");
    } catch { setListStatus("error"); }
  }

  React.useEffect(() => { loadCards(); }, []);

  async function issueCard() {
    setIssueError(null);
    setIssuing(true);
    try {
      const res = await api.request<{ card: CardResponse }>("/api/v1/cards", { method: "POST" });
      setIssuedCard(res.card);
      loadCards();
    } catch (err) {
      setIssueError(err instanceof PlatformApiError ? err.message : "Card issuance failed.");
    } finally {
      setIssuing(false);
    }
  }

  return (
    <>
      <PageHeader breadcrumbs={<span>Wallet / Cards</span>} subtitle="Issue and manage virtual cards linked to your wallet." title="Cards" />
      <div className="eu-stack">
        {issuedCard && (
          <Panel actions={<Button size="sm" variant="ghost" onClick={() => setIssuedCard(null)}>Dismiss</Button>} title="Card issued">
            <div className="eu-card-tile">
              <div className="eu-card-pan">•••• •••• •••• {issuedCard.last4}</div>
              <div className="eu-card-meta">
                <span>{issuedCard.expirationMonth.toString().padStart(2, "0")}/{issuedCard.expirationYear}</span>
                <Badge tone={issuedCard.state === "ACTIVE" ? "success" : "neutral"}>{issuedCard.state}</Badge>
              </div>
              <div className="eu-kv eu-card-kv">
                <div><span>Card ID</span><strong className="mono">{issuedCard.id}</strong></div>
                <div><span>BIN</span><strong className="mono">{issuedCard.bin}</strong></div>
              </div>
            </div>
          </Panel>
        )}
        <Panel
          actions={
            <Button disabled={issuing} size="sm" variant="primary" onClick={issueCard}>
              {issuing ? "Issuing…" : "Issue virtual card"}
            </Button>
          }
          title="My cards"
        >
          {issueError && <div className="eu-error">{issueError}</div>}
          <p className="eu-muted">A virtual card will be issued and linked to your wallet. KYC approval is required.</p>
          {listStatus === "loading" && <EmptyState body="Fetching your cards." title="Loading…" />}
          {listStatus === "error" && <EmptyState body="Could not load cards." title="Failed to load" />}
          {listStatus === "ready" && (
            <DataTable<CardResponse>
              columns={[
                { key: "pan", header: "Card", render: (r) => <span className="mono">•••• •••• •••• {r.last4}</span> },
                { key: "exp", header: "Expires", render: (r) => `${r.expirationMonth.toString().padStart(2, "0")}/${r.expirationYear}` },
                { key: "bin", header: "BIN", className: "mono", render: (r) => r.bin },
                { key: "state", header: "State", render: (r) => <Badge tone={r.state === "ACTIVE" ? "success" : "neutral"}>{r.state}</Badge> },
                { key: "id", header: "ID", className: "mono", render: (r) => r.id.slice(0, 8) + "…" },
              ]}
              emptyState={<EmptyState body="No cards issued yet. Click Issue virtual card to create one." title="No cards" />}
              rowKey={(r) => r.id}
              rows={cards}
            />
          )}
        </Panel>
      </div>
    </>
  );
}

// ─── kyc ───────────────────────────────────────────────────────────────────

function KycPage() {
  const [kycResult, setKycResult] = React.useState<KycStartResponse | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  async function startKyc() {
    setError(null);
    setBusy(true);
    try {
      const res = await api.request<KycStartResponse>("/api/v1/kyc/start", { method: "POST" });
      setKycResult(res);
    } catch (err) {
      setError(err instanceof PlatformApiError ? err.message : "KYC start failed.");
    } finally {
      setBusy(false);
    }
  }

  const isConfigured = kycResult?.configurationStatus === "CONFIGURED";

  return (
    <>
      <PageHeader breadcrumbs={<span>Account / Identity verification</span>} subtitle="Verify your identity to unlock all wallet features including card issuance." title="Identity verification" />
      <div className="eu-stack">
        {kycResult && (
          <Panel title="KYC status">
            <div className="eu-kv">
              <div><span>Status</span><strong><Badge tone={kycTone(kycResult.status)}>{kycResult.status}</Badge></strong></div>
              {kycResult.levelName && <div><span>Level</span><strong>{kycResult.levelName}</strong></div>}
              {kycResult.vendorApplicantId && <div><span>Applicant ID</span><strong className="mono">{kycResult.vendorApplicantId}</strong></div>}
            </div>
            {!isConfigured && (
              <div className="eu-hold-notice">
                <strong>Verification pending vendor setup</strong>
                <p className="eu-muted">Identity verification is not yet fully activated for this account. No further action is required from you at this time — you will be notified when it becomes available.</p>
              </div>
            )}
            {isConfigured && kycResult.status !== "APPROVED" && (
              <div className="eu-hold-notice">
                <strong>Continue your verification</strong>
                <p className="eu-muted">Your verification session is active. Complete document upload in the verification flow to proceed.</p>
              </div>
            )}
            {kycResult.status === "APPROVED" && (
              <div className="eu-hold-notice">
                <strong>Identity verified</strong>
                <p className="eu-muted">Your identity has been verified. You can now issue virtual cards.</p>
              </div>
            )}
          </Panel>
        )}
        <Panel title="Identity verification">
          <div className="eu-form">
            <p className="eu-muted">
              {kycResult
                ? "Check your current verification status or resume your session."
                : "Start the identity verification process. KYC approval is required to issue virtual cards and access higher deposit limits."}
            </p>
            {error && <div className="eu-error">{error}</div>}
            <Toolbar>
              <Button disabled={busy} variant="primary" onClick={startKyc}>
                {busy ? "Checking…" : kycResult ? "Refresh status" : "Start verification"}
              </Button>
            </Toolbar>
          </div>
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
