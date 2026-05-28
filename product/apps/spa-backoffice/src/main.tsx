import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import {
  AppShell,
  AppSidebar,
  AppSidebarItem,
  Badge,
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
  TextArea,
  Toolbar,
  ToolbarSpacer,
  surfaceLabels,
} from "@minifin/ui";
import "@minifin/ui/styles.css";
import {
  PlatformApiError,
  backofficeApi,
  type ActorControlRequest,
  type ActorControlResponse,
  type AmlAlert,
  type BackofficeAuditFeedItem,
  type BackofficeChargebackDetail,
  type BackofficeMeResponse,
  type ChargebackDispute,
  type KycCase,
  type ManualDeposit,
  type ManualWithdrawal,
  type ReadAuditProbeResponse,
  type SanctionsHit,
} from "./backofficeApi";
import {
  clearBackofficeSession,
  loadBackofficeSession,
  loginBackoffice,
  refreshBackofficeSession,
  type BackofficeSession,
} from "./backofficeAuth";
import "./styles.css";

type BackofficeRouteKey =
  | "login"
  | "work-queue"
  | "manual-deposits"
  | "manual-withdrawals"
  | "kyc-queue"
  | "aml-alerts"
  | "sanctions-hits"
  | "chargebacks"
  | "audit-log"
  | "actor-controls";

type SessionStatus = "bootstrapping" | "ready";

interface BackofficeAppContextValue {
  session: BackofficeSession | null;
  setSession: (session: BackofficeSession | null) => void;
  sessionStatus: SessionStatus;
  logout: () => void;
}

interface QueueRow {
  id: string;
  route: BackofficeRouteKey;
  type: string;
  subject: string;
  priority: "critical" | "warning" | "info";
  age: string;
  state: string;
}

const BackofficeAppContext = React.createContext<BackofficeAppContextValue | null>(null);

const dateFormatter = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeStyle: "short",
});

const navItems: Array<{
  key: BackofficeRouteKey;
  label: string;
  section: string;
  requiresCompliance?: boolean;
}> = [
  { key: "work-queue", label: "Work queue", section: "Operations" },
  { key: "manual-deposits", label: "Manual deposits", section: "Operations" },
  { key: "manual-withdrawals", label: "Manual withdrawals", section: "Operations" },
  { key: "kyc-queue", label: "KYC queue", section: "Compliance" },
  { key: "aml-alerts", label: "AML alerts", section: "Compliance" },
  { key: "sanctions-hits", label: "Sanctions hits", section: "Compliance", requiresCompliance: true },
  { key: "chargebacks", label: "Chargeback arbitration", section: "Chargebacks" },
  { key: "audit-log", label: "Audit log", section: "Audit" },
  { key: "actor-controls", label: "Actor controls", section: "Audit" },
];

function useBackofficeApp() {
  const context = React.useContext(BackofficeAppContext);
  if (!context) {
    throw new Error("Backoffice app context is not available.");
  }
  return context;
}

function backofficeRouteKey(pathname: string): BackofficeRouteKey {
  if (pathname === "/login") return "login";
  if (pathname === "/manual-deposits") return "manual-deposits";
  if (pathname === "/manual-withdrawals") return "manual-withdrawals";
  if (pathname === "/kyc-queue") return "kyc-queue";
  if (pathname === "/aml-alerts") return "aml-alerts";
  if (pathname === "/sanctions-hits") return "sanctions-hits";
  if (pathname === "/chargebacks") return "chargebacks";
  if (pathname === "/audit-log") return "audit-log";
  if (pathname === "/actor-controls") return "actor-controls";
  return "work-queue";
}

function routePath(route: BackofficeRouteKey): string {
  return route === "work-queue" ? "/work-queue" : `/${route}`;
}

function isComplianceProfile(profile: BackofficeMeResponse | undefined): boolean {
  return Boolean(profile?.roles.some((role) => role === "compliance_officer" || role === "senior_compliance"));
}

function formatError(error: unknown): string {
  if (error instanceof PlatformApiError) {
    return error.errors[0]?.message ?? error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Unexpected error.";
}

function formatDateTime(value?: string | null): string {
  if (!value) {
    return "—";
  }
  return dateFormatter.format(new Date(value));
}

function formatAmount(amount: string, currency: string): string {
  const numeric = Number(amount);
  if (Number.isNaN(numeric)) {
    return `${amount} ${currency}`;
  }
  return new Intl.NumberFormat("en-GB", {
    style: "currency",
    currency,
  }).format(numeric);
}

function formatAge(value: string): string {
  const now = Date.now();
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) {
    return "—";
  }
  const minutes = Math.max(1, Math.round((now - then) / 60000));
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  if (hours < 24) {
    return remainder === 0 ? `${hours}h` : `${hours}h ${remainder}m`;
  }
  const days = Math.floor(hours / 24);
  return `${days}d ${hours % 24}h`;
}

function statusTone(status: string): "neutral" | "warning" | "danger" | "success" | "info" | "accent" {
  const normalized = status.toUpperCase();
  if (normalized.includes("FAIL") || normalized.includes("DENY")) {
    return "danger";
  }
  if (normalized.includes("REJECT") || normalized.includes("BLOCK") || normalized.includes("FROZEN")) {
    return "danger";
  }
  if (normalized.includes("OPEN") || normalized.includes("REVIEW") || normalized.includes("HELD")) {
    return "warning";
  }
  if (normalized.includes("SUCCESS") || normalized.includes("ALLOW")) {
    return "success";
  }
  if (normalized.includes("CLEAR") || normalized.includes("APPROV") || normalized.includes("COMPLET")) {
    return "success";
  }
  if (normalized.includes("ESCALAT") || normalized.includes("SAR")) {
    return "accent";
  }
  return "info";
}

function severityTone(severity: string): "neutral" | "warning" | "danger" | "info" {
  const normalized = severity.toUpperCase();
  if (normalized === "HIGH" || normalized === "CRITICAL") return "danger";
  if (normalized === "MEDIUM") return "warning";
  return "info";
}

function riskPriority(status: string, fallback: "warning" | "info" = "info"): "critical" | "warning" | "info" {
  const normalized = status.toUpperCase();
  if (normalized.includes("OPEN") || normalized.includes("REVIEW")) return "critical";
  if (fallback === "warning") return "warning";
  return "info";
}

function updateSelectedId<T extends { id: string }>(items: T[], selectedId: string | null): string | null {
  if (items.length === 0) {
    return null;
  }
  if (selectedId && items.some((item) => item.id === selectedId)) {
    return selectedId;
  }
  return items[0].id;
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { session, sessionStatus } = useBackofficeApp();
  const location = useLocation();

  if (sessionStatus === "bootstrapping") {
    return <LoadingPage title="Restoring session" body="Checking the stored Keycloak bearer token before loading backoffice routes." />;
  }

  if (!session) {
    return <Navigate replace state={{ from: location.pathname }} to="/login" />;
  }

  return <>{children}</>;
}

function useRemoteData<T>(load: () => Promise<T>, initialData: T) {
  const [data, setData] = React.useState<T>(initialData);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [version, setVersion] = React.useState(0);

  const reload = React.useCallback(() => {
    setVersion((current) => current + 1);
  }, []);

  React.useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    load()
      .then((nextData) => {
        if (!cancelled) {
          setData(nextData);
        }
      })
      .catch((nextError) => {
        if (!cancelled) {
          setError(formatError(nextError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [load, version]);

  return { data, setData, loading, error, reload };
}

function useDetailData<T>(selectedId: string | null, load: (id: string) => Promise<T>) {
  const [data, setData] = React.useState<T | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!selectedId) {
      setData(null);
      setError(null);
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    load(selectedId)
      .then((nextData) => {
        if (!cancelled) {
          setData(nextData);
        }
      })
      .catch((nextError) => {
        if (!cancelled) {
          setError(formatError(nextError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [load, selectedId]);

  return { data, setData, loading, error };
}

function BackofficeRoot() {
  const initialSession = React.useMemo(() => loadBackofficeSession(), []);
  const [session, setSession] = React.useState<BackofficeSession | null>(initialSession);
  const [sessionStatus, setSessionStatus] = React.useState<SessionStatus>(initialSession ? "bootstrapping" : "ready");

  React.useEffect(() => {
    if (!initialSession) {
      return;
    }

    let cancelled = false;
    refreshBackofficeSession(initialSession.accessToken)
      .then((nextSession) => {
        if (!cancelled) {
          setSession(nextSession);
        }
      })
      .catch(() => {
        if (!cancelled) {
          clearBackofficeSession();
          setSession(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setSessionStatus("ready");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [initialSession]);

  const logout = React.useCallback(() => {
    clearBackofficeSession();
    setSession(null);
  }, []);

  const contextValue = React.useMemo(
    () => ({
      session,
      setSession,
      sessionStatus,
      logout,
    }),
    [logout, session, sessionStatus],
  );

  return (
    <BackofficeAppContext.Provider value={contextValue}>
      <BrowserRouter>
        <BackofficeLayout />
      </BrowserRouter>
    </BackofficeAppContext.Provider>
  );
}

function BackofficeLayout() {
  const { session, logout } = useBackofficeApp();
  const location = useLocation();
  const navigate = useNavigate();
  const activeKey = backofficeRouteKey(location.pathname);
  const compliance = isComplianceProfile(session?.profile);

  const sections = [
    ...Array.from(new Set(navItems.map((item) => item.section))).map((section) => ({
      title: section,
      items: navItems
        .filter((item) => item.section === section)
        .map((item) => (
          <AppSidebarItem
            key={item.key}
            active={item.key === activeKey}
            disabled={Boolean(item.requiresCompliance && !compliance)}
            label={item.label}
            meta={item.requiresCompliance ? <Badge tone={compliance ? "success" : "warning"}>CO</Badge> : undefined}
            onClick={() => navigate(routePath(item.key))}
          />
        )),
    })),
    {
      title: "Session",
      items: [
        session ? (
          <AppSidebarItem key="signout" label="Sign out" onClick={logout} />
        ) : (
          <AppSidebarItem key="signin" active={activeKey === "login"} label="Sign in" onClick={() => navigate("/login")} />
        ),
      ],
    },
  ];

  return (
    <AppShell
      surface="backoffice"
      aside={<AuditRail />}
      sidebar={
        <AppSidebar
          environmentLabel="LOCAL BACKOFFICE"
          sections={sections}
          surface="backoffice"
          userMeta={session?.profile.email ?? "No active session"}
          userName={session ? session.profile.roles.join(" / ") : "Signed out"}
        />
      }
    >
      <Routes>
        <Route element={<LoginPage />} path="/login" />
        <Route
          element={
            <ProtectedRoute>
              <WorkQueuePage />
            </ProtectedRoute>
          }
          path="/work-queue"
        />
        <Route
          element={
            <ProtectedRoute>
              <ManualDepositsPage />
            </ProtectedRoute>
          }
          path="/manual-deposits"
        />
        <Route
          element={
            <ProtectedRoute>
              <ManualWithdrawalsPage />
            </ProtectedRoute>
          }
          path="/manual-withdrawals"
        />
        <Route
          element={
            <ProtectedRoute>
              <KycQueuePage />
            </ProtectedRoute>
          }
          path="/kyc-queue"
        />
        <Route
          element={
            <ProtectedRoute>
              <AmlAlertsPage />
            </ProtectedRoute>
          }
          path="/aml-alerts"
        />
        <Route
          element={
            <ProtectedRoute>
              <SanctionsHitsPage />
            </ProtectedRoute>
          }
          path="/sanctions-hits"
        />
        <Route
          element={
            <ProtectedRoute>
              <ChargebackArbitrationPage />
            </ProtectedRoute>
          }
          path="/chargebacks"
        />
        <Route
          element={
            <ProtectedRoute>
              <AuditLogPage />
            </ProtectedRoute>
          }
          path="/audit-log"
        />
        <Route
          element={
            <ProtectedRoute>
              <ActorControlsPage />
            </ProtectedRoute>
          }
          path="/actor-controls"
        />
        <Route element={<Navigate replace to={session ? "/work-queue" : "/login"} />} path="*" />
      </Routes>
    </AppShell>
  );
}

function LoadingPage({ title, body }: { title: string; body: string }) {
  return (
    <Panel title={title}>
      <div className="backoffice-loading-copy">{body}</div>
    </Panel>
  );
}

function PageError({ message }: { message: string }) {
  return (
    <div className="backoffice-inline-error">
      <strong>Request failed.</strong>
      <span>{message}</span>
    </div>
  );
}

function PageSection({
  title,
  screenId,
  subtitle,
  rightSlot,
  children,
}: {
  title: string;
  screenId: string;
  subtitle: string;
  rightSlot?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <>
      <PageHeader
        breadcrumbs={<span>Backoffice / {title}</span>}
        rightSlot={rightSlot}
        screenId={screenId}
        subtitle={subtitle}
        title={title}
      />
      {children}
    </>
  );
}

function LoginPage() {
  const { session, setSession } = useBackofficeApp();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = React.useState("operator");
  const [password, setPassword] = React.useState("password123");
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const redirectState = location.state as { from?: string } | null;
  const redirectTarget = redirectState?.from && redirectState.from !== "/login" ? redirectState.from : "/work-queue";

  React.useEffect(() => {
    if (session) {
      navigate("/work-queue", { replace: true });
    }
  }, [navigate, session]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const nextSession = await loginBackoffice(username, password);
      setSession(nextSession);
      navigate(redirectTarget, { replace: true });
    } catch (submitError) {
      setError(formatError(submitError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <PageHeader
        breadcrumbs={<span>Backoffice / Access / Sign in</span>}
        rightSlot={<Badge tone="info">Keycloak</Badge>}
        screenId="BOF-UI-01"
        subtitle="Sign in with your backoffice operator credentials."
        title="Sign in"
      />
      <div className="backoffice-page-grid">
        <Panel title="Operator login">
          <form className="backoffice-form" onSubmit={handleSubmit}>
            <Field hint="Local seeded users: operator, compliance." label="Username">
              <Input onChange={(event) => setUsername(event.target.value)} value={username} />
            </Field>
            <Field label="Password">
              <Input onChange={(event) => setPassword(event.target.value)} type="password" value={password} />
            </Field>
            {error ? <PageError message={error} /> : null}
            <Toolbar>
              <Button disabled={submitting} type="submit" variant="primary">
                {submitting ? "Signing in..." : "Sign in"}
              </Button>
              <Button disabled={submitting} onClick={() => setUsername("operator")} size="sm" variant="ghost">
                operator
              </Button>
              <Button disabled={submitting} onClick={() => setUsername("compliance")} size="sm" variant="ghost">
                compliance
              </Button>
            </Toolbar>
          </form>
        </Panel>
      </div>
    </>
  );
}

function WorkQueuePage() {
  const { session } = useBackofficeApp();
  const navigate = useNavigate();
  const token = session!.accessToken;
  const canViewSanctions = isComplianceProfile(session?.profile);

  const loadQueue = React.useCallback(async () => {
    const [deposits, withdrawals, kyc, aml, sanctions] = await Promise.all([
      backofficeApi.listManualDeposits(token),
      backofficeApi.listManualWithdrawals(token),
      backofficeApi.listKycCases(token),
      backofficeApi.listAmlAlerts(token),
      canViewSanctions ? backofficeApi.listSanctionsHits(token) : Promise.resolve([] as SanctionsHit[]),
    ]);

    return { deposits, withdrawals, kyc, aml, sanctions };
  }, [canViewSanctions, token]);

  const { data, loading, error, reload } = useRemoteData(loadQueue, {
    deposits: [] as ManualDeposit[],
    withdrawals: [] as ManualWithdrawal[],
    kyc: [] as KycCase[],
    aml: [] as AmlAlert[],
    sanctions: [] as SanctionsHit[],
  });

  const allQueueRows = React.useMemo<QueueRow[]>(() => {
    return [
      ...data.kyc.map((item) => ({
        id: item.id,
        route: "kyc-queue" as const,
        type: "KYC case",
        subject: item.endUserId,
        priority: riskPriority(item.status),
        age: formatAge(item.updatedAt),
        state: item.status,
      })),
      ...data.aml.map((item) => ({
        id: item.id,
        route: "aml-alerts" as const,
        type: `AML ${item.ruleCode}`,
        subject: item.endUserId,
        priority: (item.severity === "HIGH" ? "critical" : "warning") as QueueRow["priority"],
        age: formatAge(item.updatedAt),
        state: item.status,
      })),
      ...data.sanctions.map((item) => ({
        id: item.id,
        route: "sanctions-hits" as const,
        type: "Sanctions hit",
        subject: item.matchedName ?? item.endUserId,
        priority: "critical" as QueueRow["priority"],
        age: formatAge(item.updatedAt),
        state: item.status,
      })),
      ...data.deposits.map((item) => ({
        id: item.depositId,
        route: "manual-deposits" as const,
        type: "Manual deposit",
        subject: item.userId,
        priority: riskPriority(item.state, "warning"),
        age: formatAge(item.createdAt),
        state: item.state,
      })),
      ...data.withdrawals.map((item) => ({
        id: item.withdrawalId,
        route: "manual-withdrawals" as const,
        type: "Manual withdrawal",
        subject: item.userId,
        priority: riskPriority(item.state, "warning"),
        age: formatAge(item.heldAt ?? item.createdAt),
        state: item.state,
      })),
    ];
  }, [data]);

  const queueRows = allQueueRows.slice(0, 20);

  return (
    <>
      <PageHeader
        breadcrumbs={<span>Backoffice / Operations / Work queue</span>}
        screenId="BOF-UI-02"
        subtitle="Live summary of open operational items. Click a queue to begin review."
        title={surfaceLabels.backoffice}
      >
        <StatGrid columns={4}>
          <StatCard label="Open KYC cases" meta="manual review queue" tone="danger" value={data.kyc.length} />
          <StatCard label="AML alerts" meta="reviewable alerts" tone="warning" value={data.aml.length} />
          <StatCard
            label="Sanctions hits"
            meta={canViewSanctions ? "compliance queue" : "requires compliance role"}
            tone="info"
            value={canViewSanctions ? data.sanctions.length : "—"}
          />
          <StatCard
            label="Manual ops"
            meta="deposits + withdrawals"
            tone="accent"
            value={data.deposits.length + data.withdrawals.length}
          />
        </StatGrid>
      </PageHeader>
      <div className="backoffice-stack">
        <Panel
          actions={
            <Toolbar>
              {allQueueRows.length > 20 && (
                <Badge tone="warning">Showing 20 of {allQueueRows.length}</Badge>
              )}
              <ToolbarSpacer />
              <Button size="sm" variant="secondary" onClick={reload}>
                Refresh
              </Button>
            </Toolbar>
          }
          title="Open items"
        >
          {loading ? <div className="backoffice-loading-copy">Loading queue snapshot...</div> : null}
          {error ? <PageError message={error} /> : null}
          {!loading && !error ? (
            <DataTable
              columns={[
                { key: "id", header: "ID", className: "mono", render: (row) => row.id.slice(0, 8) + "…" },
                { key: "type", header: "Type", render: (row) => row.type },
                { key: "subject", header: "Subject", className: "mono", render: (row) => row.subject.slice(0, 8) + "…" },
                { key: "state", header: "State", render: (row) => <Badge tone={statusTone(row.state)}>{row.state}</Badge> },
                {
                  key: "priority",
                  header: "Priority",
                  render: (row) => (
                    <Badge tone={row.priority === "critical" ? "danger" : row.priority}>{row.priority.toUpperCase()}</Badge>
                  ),
                },
                { key: "age", header: "Age", className: "mono", render: (row) => row.age },
                {
                  key: "action",
                  header: "Action",
                  render: (row) => (
                    <Button size="sm" variant="ghost" onClick={() => navigate(routePath(row.route))}>
                      Open queue
                    </Button>
                  ),
                },
              ]}
              emptyState={<EmptyState body="No reviewable items are currently open." title="Queue is empty" />}
              rowKey={(row) => `${row.route}:${row.id}`}
              rows={queueRows}
            />
          ) : null}
        </Panel>
      </div>
    </>
  );
}

function ManualDepositsPage() {
  const { session } = useBackofficeApp();
  const token = session!.accessToken;
  const loadDeposits = React.useCallback(() => backofficeApi.listManualDeposits(token), [token]);
  const { data, loading, error, reload } = useRemoteData(loadDeposits, [] as ManualDeposit[]);
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [reason, setReason] = React.useState("Source-of-funds review completed; operator decision recorded.");
  const [pendingDecision, setPendingDecision] = React.useState<string | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);

  React.useEffect(() => {
    setSelectedId((current) => updateSelectedId(data.map((item) => ({ id: item.depositId })), current));
  }, [data]);

  const selected = data.find((item) => item.depositId === selectedId) ?? null;

  async function submitDecision(decision: "APPROVE" | "REJECT") {
    if (!selected) {
      return;
    }
    setPendingDecision(decision);
    setActionError(null);
    try {
      await backofficeApi.decideManualDeposit(token, selected.depositId, decision, reason);
      reload();
    } catch (submitError) {
      setActionError(formatError(submitError));
    } finally {
      setPendingDecision(null);
    }
  }

  return (
    <PageSection
      screenId="BOF-UI-03"
      subtitle="Review and approve or reject pending deposit requests."
      title="Manual deposits"
    >
      <TwoColumnPage
        detailPanel={
          <Panel
            actions={<Badge tone={selected ? statusTone(selected.state) : "neutral"}>{selected?.state ?? "NO_SELECTION"}</Badge>}
            title="Decision workspace"
          >
            {!selected ? (
              <EmptyState body="Select a deposit from the queue to review amount, source-of-funds flags, and the operator rationale." title="No deposit selected" />
            ) : (
              <div className="backoffice-detail-stack">
                <KeyValueList
                  items={[
                    ["Deposit ID", selected.depositId],
                    ["End-user", selected.userId],
                    ["Amount", formatAmount(selected.amount, selected.currency)],
                    ["Created", formatDateTime(selected.createdAt)],
                    ["Source of funds", selected.sourceOfFundsRequired ? (selected.sourceOfFundsSubmitted ? "Submitted" : "Required") : "Not required"],
                    ["Last reason", selected.reason ?? "—"],
                  ]}
                />
                <Field hint="Required by the backend." label="Decision reason">
                  <TextArea onChange={(event) => setReason(event.target.value)} rows={5} value={reason} />
                </Field>
                {actionError ? <PageError message={actionError} /> : null}
                <Toolbar>
                  <Button disabled={pendingDecision !== null} onClick={() => submitDecision("APPROVE")} variant="primary">
                    {pendingDecision === "APPROVE" ? "Submitting..." : "Approve"}
                  </Button>
                  <Button disabled={pendingDecision !== null} onClick={() => submitDecision("REJECT")} variant="danger">
                    {pendingDecision === "REJECT" ? "Submitting..." : "Reject"}
                  </Button>
                </Toolbar>
              </div>
            )}
          </Panel>
        }
        listPanel={
          <Panel
            actions={
              <Toolbar>
                <Badge tone="info">{data.length} pending</Badge>
                <ToolbarSpacer />
                <Button size="sm" variant="secondary" onClick={reload}>
                  Refresh
                </Button>
              </Toolbar>
            }
            title="Pending deposit requests"
          >
            {loading ? <div className="backoffice-loading-copy">Loading deposits...</div> : null}
            {error ? <PageError message={error} /> : null}
            {!loading ? (
              <DataTable
                columns={[
                  { key: "id", header: "Deposit", className: "mono", render: (row) => row.depositId.slice(0, 8) + "…" },
                  { key: "user", header: "End-user", className: "mono", render: (row) => row.userId.slice(0, 8) + "…" },
                  { key: "amount", header: "Amount", render: (row) => formatAmount(row.amount, row.currency) },
                  { key: "state", header: "State", render: (row) => <Badge tone={statusTone(row.state)}>{row.state}</Badge> },
                  {
                    key: "sofs",
                    header: "SOF",
                    render: (row) => (
                      <Badge tone={row.sourceOfFundsRequired && !row.sourceOfFundsSubmitted ? "warning" : "success"}>
                        {row.sourceOfFundsRequired ? (row.sourceOfFundsSubmitted ? "SUBMITTED" : "REQUIRED") : "CLEAR"}
                      </Badge>
                    ),
                  },
                  { key: "createdAt", header: "Created", render: (row) => formatDateTime(row.createdAt) },
                  {
                    key: "action",
                    header: "Action",
                    render: (row) => (
                      <Button size="sm" variant={row.depositId === selectedId ? "primary" : "ghost"} onClick={() => setSelectedId(row.depositId)}>
                        Review
                      </Button>
                    ),
                  },
                ]}
                emptyState={<EmptyState body="The manual deposit queue is empty." title="No pending deposits" />}
                rowKey={(row) => row.depositId}
                rows={data}
              />
            ) : null}
          </Panel>
        }
      />
    </PageSection>
  );
}

function ManualWithdrawalsPage() {
  const { session } = useBackofficeApp();
  const token = session!.accessToken;
  const loadWithdrawals = React.useCallback(() => backofficeApi.listManualWithdrawals(token), [token]);
  const { data, loading, error, reload } = useRemoteData(loadWithdrawals, [] as ManualWithdrawal[]);
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [reason, setReason] = React.useState("Withdrawal review completed; hold can be finalized.");
  const [pendingDecision, setPendingDecision] = React.useState<string | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);

  React.useEffect(() => {
    setSelectedId((current) => updateSelectedId(data.map((item) => ({ id: item.withdrawalId })), current));
  }, [data]);

  const selected = data.find((item) => item.withdrawalId === selectedId) ?? null;

  async function submitDecision(decision: "COMPLETE" | "REJECT") {
    if (!selected) {
      return;
    }
    setPendingDecision(decision);
    setActionError(null);
    try {
      await backofficeApi.decideManualWithdrawal(token, selected.withdrawalId, decision, reason);
      reload();
    } catch (submitError) {
      setActionError(formatError(submitError));
    } finally {
      setPendingDecision(null);
    }
  }

  return (
    <PageSection
      screenId="BOF-UI-04"
      subtitle="Review held withdrawals and record COMPLETE or REJECT decisions."
      title="Manual withdrawals"
    >
      <TwoColumnPage
        detailPanel={
          <Panel
            actions={<Badge tone={selected ? statusTone(selected.state) : "neutral"}>{selected?.state ?? "NO_SELECTION"}</Badge>}
            title="Decision workspace"
          >
            {!selected ? (
              <EmptyState body="Select a held withdrawal to review hold timing, release/completion journals, and the operator rationale." title="No withdrawal selected" />
            ) : (
              <div className="backoffice-detail-stack">
                <KeyValueList
                  items={[
                    ["Withdrawal ID", selected.withdrawalId],
                    ["End-user", selected.userId],
                    ["Amount", formatAmount(selected.amount, selected.currency)],
                    ["Created", formatDateTime(selected.createdAt)],
                    ["Held at", formatDateTime(selected.heldAt)],
                    ["Last reason", selected.reason ?? "—"],
                  ]}
                />
                <Field hint="COMPLETE finalizes the payout, REJECT releases funds back to the wallet." label="Decision reason">
                  <TextArea onChange={(event) => setReason(event.target.value)} rows={5} value={reason} />
                </Field>
                {actionError ? <PageError message={actionError} /> : null}
                <Toolbar>
                  <Button disabled={pendingDecision !== null} onClick={() => submitDecision("COMPLETE")} variant="primary">
                    {pendingDecision === "COMPLETE" ? "Submitting..." : "Complete"}
                  </Button>
                  <Button disabled={pendingDecision !== null} onClick={() => submitDecision("REJECT")} variant="danger">
                    {pendingDecision === "REJECT" ? "Submitting..." : "Reject"}
                  </Button>
                </Toolbar>
              </div>
            )}
          </Panel>
        }
        listPanel={
          <Panel
            actions={
              <Toolbar>
                <Badge tone="info">{data.length} held</Badge>
                <ToolbarSpacer />
                <Button size="sm" variant="secondary" onClick={reload}>
                  Refresh
                </Button>
              </Toolbar>
            }
            title="Held withdrawal requests"
          >
            {loading ? <div className="backoffice-loading-copy">Loading withdrawals...</div> : null}
            {error ? <PageError message={error} /> : null}
            {!loading ? (
              <DataTable
                columns={[
                  { key: "id", header: "Withdrawal", className: "mono", render: (row) => row.withdrawalId.slice(0, 8) + "…" },
                  { key: "user", header: "End-user", className: "mono", render: (row) => row.userId.slice(0, 8) + "…" },
                  { key: "amount", header: "Amount", render: (row) => formatAmount(row.amount, row.currency) },
                  { key: "state", header: "State", render: (row) => <Badge tone={statusTone(row.state)}>{row.state}</Badge> },
                  { key: "heldAt", header: "Held at", render: (row) => formatDateTime(row.heldAt) },
                  {
                    key: "action",
                    header: "Action",
                    render: (row) => (
                      <Button size="sm" variant={row.withdrawalId === selectedId ? "primary" : "ghost"} onClick={() => setSelectedId(row.withdrawalId)}>
                        Review
                      </Button>
                    ),
                  },
                ]}
                emptyState={<EmptyState body="The withdrawal hold queue is empty." title="No held withdrawals" />}
                rowKey={(row) => row.withdrawalId}
                rows={data}
              />
            ) : null}
          </Panel>
        }
      />
    </PageSection>
  );
}

function KycQueuePage() {
  const { session } = useBackofficeApp();
  const token = session!.accessToken;
  const loadCases = React.useCallback(() => backofficeApi.listKycCases(token), [token]);
  const { data, loading, error, reload } = useRemoteData(loadCases, [] as KycCase[]);
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [decision, setDecision] = React.useState("APPROVE");
  const [rationale, setRationale] = React.useState("Manual KYC review completed with recorded evidence and compliance rationale.");
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);

  React.useEffect(() => {
    setSelectedId((current) => updateSelectedId(data, current));
  }, [data]);

  const loadDetail = React.useCallback((caseId: string) => backofficeApi.getKycCase(token, caseId), [token]);
  const detail = useDetailData(selectedId, loadDetail);

  async function handleSubmit() {
    if (!selectedId) {
      return;
    }
    setSubmitting(true);
    setActionError(null);
    try {
      await backofficeApi.decideKycCase(token, selectedId, decision, rationale);
      reload();
    } catch (submitError) {
      setActionError(formatError(submitError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageSection
      screenId="BOF-UI-05"
      subtitle="Review KYC cases awaiting manual decision."
      title="KYC queue"
    >
      <TwoColumnPage
        detailPanel={
          <Panel
            actions={<Badge tone={detail.data ? statusTone(detail.data.status) : "neutral"}>{detail.data?.status ?? "NO_SELECTION"}</Badge>}
            title="Case detail"
          >
            {detail.loading ? <div className="backoffice-loading-copy">Loading case detail...</div> : null}
            {detail.error ? <PageError message={detail.error} /> : null}
            {!detail.loading && !detail.data ? (
              <EmptyState body="Select a KYC case to view vendor metadata, review fields, and decision controls." title="No KYC case selected" />
            ) : null}
            {detail.data ? (
              <div className="backoffice-detail-stack">
                <KeyValueList
                  items={[
                    ["Case ID", detail.data.id],
                    ["End-user", detail.data.endUserId],
                    ["Vendor", detail.data.vendor],
                    ["External user", detail.data.externalUserId],
                    ["Applicant ID", detail.data.vendorApplicantId ?? "—"],
                    ["Level", detail.data.levelName ?? "—"],
                    ["Review answer", detail.data.reviewAnswer ?? "—"],
                    ["Reject type", detail.data.reviewRejectType ?? "—"],
                    ["Vendor note", detail.data.reviewModerationComment ?? "—"],
                    ["Updated", formatDateTime(detail.data.updatedAt)],
                  ]}
                />
                <Field label="Decision">
                  <Select onChange={(event) => setDecision(event.target.value)} value={decision}>
                    <option value="APPROVE">APPROVE</option>
                    <option value="REJECT">REJECT</option>
                    <option value="REQUEST_RESUBMIT">REQUEST_RESUBMIT</option>
                  </Select>
                </Field>
                <Field hint="Minimum 20 characters required." label="Rationale">
                  <TextArea onChange={(event) => setRationale(event.target.value)} rows={5} value={rationale} />
                </Field>
                {actionError ? <PageError message={actionError} /> : null}
                <Toolbar>
                  <Button disabled={submitting || rationale.trim().length < 20} onClick={handleSubmit} variant="primary">
                    {submitting ? "Submitting..." : "Record decision"}
                  </Button>
                </Toolbar>
              </div>
            ) : null}
          </Panel>
        }
        listPanel={
          <Panel
            actions={
              <Toolbar>
                <Badge tone="info">{data.length} open</Badge>
                <ToolbarSpacer />
                <Button size="sm" variant="secondary" onClick={reload}>
                  Refresh
                </Button>
              </Toolbar>
            }
            title="Manual review cases"
          >
            {loading ? <div className="backoffice-loading-copy">Loading KYC queue...</div> : null}
            {error ? <PageError message={error} /> : null}
            {!loading ? (
              <DataTable
                columns={[
                  { key: "id", header: "Case", className: "mono", render: (row) => row.id.slice(0, 8) + "…" },
                  { key: "user", header: "End-user", className: "mono", render: (row) => row.endUserId.slice(0, 8) + "…" },
                  { key: "status", header: "Status", render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge> },
                  { key: "vendor", header: "Vendor", render: (row) => row.vendor },
                  { key: "updated", header: "Updated", render: (row) => formatDateTime(row.updatedAt) },
                  {
                    key: "action",
                    header: "Action",
                    render: (row) => (
                      <Button size="sm" variant={row.id === selectedId ? "primary" : "ghost"} onClick={() => setSelectedId(row.id)}>
                        Review
                      </Button>
                    ),
                  },
                ]}
                emptyState={<EmptyState body="No KYC case is waiting for manual review." title="Queue is empty" />}
                rowKey={(row) => row.id}
                rows={data}
              />
            ) : null}
          </Panel>
        }
      />
    </PageSection>
  );
}

function AmlAlertsPage() {
  const { session } = useBackofficeApp();
  const token = session!.accessToken;
  const canMarkSar = isComplianceProfile(session?.profile);
  const loadAlerts = React.useCallback(() => backofficeApi.listAmlAlerts(token), [token]);
  const { data, loading, error, reload } = useRemoteData(loadAlerts, [] as AmlAlert[]);
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [decision, setDecision] = React.useState(canMarkSar ? "MARKED_FOR_SAR" : "CLOSED_FALSE_POSITIVE");
  const [rationale, setRationale] = React.useState("AML review completed with documented reasoning and operator evidence.");
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);

  React.useEffect(() => {
    setSelectedId((current) => updateSelectedId(data, current));
  }, [data]);

  const loadDetail = React.useCallback((alertId: string) => backofficeApi.getAmlAlert(token, alertId), [token]);
  const detail = useDetailData(selectedId, loadDetail);

  async function handleSubmit() {
    if (!selectedId) {
      return;
    }
    setSubmitting(true);
    setActionError(null);
    try {
      await backofficeApi.decideAmlAlert(token, selectedId, decision, rationale);
      reload();
    } catch (submitError) {
      setActionError(formatError(submitError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageSection
      screenId="BOF-UI-06"
      subtitle="Review AML alerts and record decisions. SAR marking requires a compliance officer role."
      title="AML alerts"
    >
      <TwoColumnPage
        detailPanel={
          <Panel
            actions={<Badge tone={detail.data ? severityTone(detail.data.severity) : "neutral"}>{detail.data?.severity ?? "NO_SELECTION"}</Badge>}
            title="Alert detail"
          >
            {detail.loading ? <div className="backoffice-loading-copy">Loading AML detail...</div> : null}
            {detail.error ? <PageError message={detail.error} /> : null}
            {!detail.loading && !detail.data ? (
              <EmptyState body="Select an alert to inspect its rule code, threshold window, and decision options." title="No alert selected" />
            ) : null}
            {detail.data ? (
              <div className="backoffice-detail-stack">
                <KeyValueList
                  items={[
                    ["Alert ID", detail.data.id],
                    ["End-user", detail.data.endUserId],
                    ["Rule", detail.data.ruleCode],
                    ["Severity", detail.data.severity],
                    ["Status", detail.data.status],
                    ["Observed count", String(detail.data.observedCount)],
                    ["Threshold count", String(detail.data.thresholdCount)],
                    ["Window start", formatDateTime(detail.data.windowStartedAt)],
                    ["Window end", formatDateTime(detail.data.windowEndedAt)],
                    ["Updated", formatDateTime(detail.data.updatedAt)],
                  ]}
                />
                <Field label="Decision">
                  <Select onChange={(event) => setDecision(event.target.value)} value={decision}>
                    <option value="CLOSED_FALSE_POSITIVE">CLOSED_FALSE_POSITIVE</option>
                    <option value="ESCALATED">ESCALATED</option>
                    <option disabled={!canMarkSar} value="MARKED_FOR_SAR">
                      MARKED_FOR_SAR
                    </option>
                  </Select>
                </Field>
                <Field hint="Minimum 20 characters required." label="Rationale">
                  <TextArea onChange={(event) => setRationale(event.target.value)} rows={5} value={rationale} />
                </Field>
                {!canMarkSar ? (
                  <div className="backoffice-inline-note">Marking for SAR requires a compliance officer role.</div>
                ) : null}
                {actionError ? <PageError message={actionError} /> : null}
                <Toolbar>
                  <Button disabled={submitting || rationale.trim().length < 20} onClick={handleSubmit} variant="primary">
                    {submitting ? "Submitting..." : "Record decision"}
                  </Button>
                </Toolbar>
              </div>
            ) : null}
          </Panel>
        }
        listPanel={
          <Panel
            actions={
              <Toolbar>
                <Badge tone="info">{data.length} reviewable</Badge>
                <ToolbarSpacer />
                <Button size="sm" variant="secondary" onClick={reload}>
                  Refresh
                </Button>
              </Toolbar>
            }
            title="Reviewable AML alerts"
          >
            {loading ? <div className="backoffice-loading-copy">Loading AML alerts...</div> : null}
            {error ? <PageError message={error} /> : null}
            {!loading ? (
              <DataTable
                columns={[
                  { key: "id", header: "Alert", className: "mono", render: (row) => row.id.slice(0, 8) + "…" },
                  { key: "rule", header: "Rule", render: (row) => row.ruleCode },
                  { key: "severity", header: "Severity", render: (row) => <Badge tone={severityTone(row.severity)}>{row.severity}</Badge> },
                  { key: "status", header: "Status", render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge> },
                  { key: "user", header: "End-user", className: "mono", render: (row) => row.endUserId.slice(0, 8) + "…" },
                  {
                    key: "action",
                    header: "Action",
                    render: (row) => (
                      <Button size="sm" variant={row.id === selectedId ? "primary" : "ghost"} onClick={() => setSelectedId(row.id)}>
                        Review
                      </Button>
                    ),
                  },
                ]}
                emptyState={<EmptyState body="There are no AML alerts awaiting review." title="Queue is empty" />}
                rowKey={(row) => row.id}
                rows={data}
              />
            ) : null}
          </Panel>
        }
      />
    </PageSection>
  );
}

function SanctionsHitsPage() {
  const { session } = useBackofficeApp();
  const token = session!.accessToken;
  const canViewSanctions = isComplianceProfile(session?.profile);
  const loadHits = React.useCallback(() => backofficeApi.listSanctionsHits(token), [token]);
  const { data, loading, error, reload } = useRemoteData(loadHits, [] as SanctionsHit[]);
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [rationale, setRationale] = React.useState("False-positive evidence reviewed and compliance exception recorded.");
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);

  React.useEffect(() => {
    setSelectedId((current) => updateSelectedId(data, current));
  }, [data]);

  const loadDetail = React.useCallback((hitId: string) => backofficeApi.getSanctionsHit(token, hitId), [token]);
  const detail = useDetailData(canViewSanctions ? selectedId : null, loadDetail);

  async function handleSubmit() {
    if (!selectedId) {
      return;
    }
    setSubmitting(true);
    setActionError(null);
    try {
      await backofficeApi.decideSanctionsHit(token, selectedId, "CLEAR_FALSE_POSITIVE", rationale);
      reload();
    } catch (submitError) {
      setActionError(formatError(submitError));
    } finally {
      setSubmitting(false);
    }
  }

  if (!canViewSanctions) {
    return (
      <PageSection
        screenId="BOF-UI-07"
        subtitle="Sanctions list and detail access requires a compliance officer role."
        title="Sanctions hits"
      >
        <Panel title="Role gate">
          <EmptyState body="Sign in with a compliance officer account to access sanctions hits." title="Compliance role required" />
        </Panel>
      </PageSection>
    );
  }

  return (
    <PageSection
      screenId="BOF-UI-07"
      subtitle="Review open sanctions hits and record false-positive clearing decisions."
      title="Sanctions hits"
    >
      <TwoColumnPage
        detailPanel={
          <Panel
            actions={<Badge tone={detail.data ? statusTone(detail.data.status) : "neutral"}>{detail.data?.status ?? "NO_SELECTION"}</Badge>}
            title="Hit detail"
          >
            {detail.loading ? <div className="backoffice-loading-copy">Loading sanctions detail...</div> : null}
            {detail.error ? <PageError message={detail.error} /> : null}
            {!detail.loading && !detail.data ? (
              <EmptyState body="Select a sanctions hit to inspect vendor metadata and record a false-positive clearing decision." title="No sanctions hit selected" />
            ) : null}
            {detail.data ? (
              <div className="backoffice-detail-stack">
                <KeyValueList
                  items={[
                    ["Hit ID", detail.data.id],
                    ["End-user", detail.data.endUserId],
                    ["Reason", detail.data.reason],
                    ["Vendor", detail.data.vendor],
                    ["Matched entity", detail.data.matchedEntityId ?? "—"],
                    ["Matched name", detail.data.matchedName ?? "—"],
                    ["Match score", String(detail.data.matchScore ?? "—")],
                    ["Request ID", detail.data.requestId ?? "—"],
                    ["Updated", formatDateTime(detail.data.updatedAt)],
                  ]}
                />
                <Field hint="Minimum 20 characters required." label="Clearing rationale">
                  <TextArea onChange={(event) => setRationale(event.target.value)} rows={5} value={rationale} />
                </Field>
                {actionError ? <PageError message={actionError} /> : null}
                <Toolbar>
                  <Button disabled={submitting || rationale.trim().length < 20} onClick={handleSubmit} variant="primary">
                    {submitting ? "Submitting..." : "Clear false positive"}
                  </Button>
                </Toolbar>
              </div>
            ) : null}
          </Panel>
        }
        listPanel={
          <Panel
            actions={
              <Toolbar>
                <Badge tone="info">{data.length} open hits</Badge>
                <ToolbarSpacer />
                <Button size="sm" variant="secondary" onClick={reload}>
                  Refresh
                </Button>
              </Toolbar>
            }
            title="Open sanctions hits"
          >
            {loading ? <div className="backoffice-loading-copy">Loading sanctions hits...</div> : null}
            {error ? <PageError message={error} /> : null}
            {!loading ? (
              <DataTable
                columns={[
                  { key: "id", header: "Hit", className: "mono", render: (row) => row.id.slice(0, 8) + "…" },
                  { key: "name", header: "Matched name", render: (row) => row.matchedName ?? "—" },
                  { key: "status", header: "Status", render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge> },
                  { key: "reason", header: "Reason", render: (row) => row.reason },
                  { key: "updated", header: "Updated", render: (row) => formatDateTime(row.updatedAt) },
                  {
                    key: "action",
                    header: "Action",
                    render: (row) => (
                      <Button size="sm" variant={row.id === selectedId ? "primary" : "ghost"} onClick={() => setSelectedId(row.id)}>
                        Review
                      </Button>
                    ),
                  },
                ]}
                emptyState={<EmptyState body="No sanctions hit currently needs review." title="Queue is empty" />}
                rowKey={(row) => row.id}
                rows={data}
              />
            ) : null}
          </Panel>
        }
      />
    </PageSection>
  );
}

function ChargebackArbitrationPage() {
  const { session } = useBackofficeApp();
  const token = session!.accessToken;
  const loadDisputes = React.useCallback(() => backofficeApi.listChargebacks(token), [token]);
  const { data, loading, error: loadError, reload } = useRemoteData(async () => (await loadDisputes()).items, [] as ChargebackDispute[]);
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [outcome, setOutcome] = React.useState("WON");
  const [rationale, setRationale] = React.useState("Evidence package reviewed; final arbitration outcome is recorded by backoffice.");
  const [submitting, setSubmitting] = React.useState(false);
  const [actionError, setActionError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (data.length === 0) {
      setSelectedId(null);
      return;
    }
    setSelectedId((current) => (current && data.some((item) => item.id === current) ? current : data[0].id));
  }, [data]);

  const loadDetail = React.useCallback((disputeId: string) => backofficeApi.getChargeback(token, disputeId), [token]);
  const detail = useDetailData(selectedId, loadDetail);

  async function handleSubmit() {
    if (!selectedId) {
      return;
    }
    setSubmitting(true);
    setActionError(null);
    try {
      const response = await backofficeApi.decideChargeback(token, selectedId, outcome, rationale);
      detail.setData((current) =>
        current
          ? {
              ...current,
              dispute: {
                ...current.dispute,
                state: response.state,
              },
            }
          : current,
      );
      reload();
    } catch (submitError) {
      setActionError(formatError(submitError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageSection
      screenId="BOF-UI-08"
      subtitle="Review chargeback evidence and record final arbitration decisions."
      title="Chargeback arbitration"
    >
      <TwoColumnPage
        detailPanel={
          <Panel
            actions={<Badge tone={detail.data ? statusTone(detail.data.dispute.state) : "neutral"}>{detail.data?.dispute.state ?? "NO_SELECTION"}</Badge>}
            title="Dispute detail"
          >
            {detail.loading ? <div className="backoffice-loading-copy">Loading dispute detail...</div> : null}
            {detail.error ? <PageError message={detail.error} /> : null}
            {!detail.loading && !detail.data ? (
              <EmptyState body="Select a dispute to inspect evidence, deadlines, and arbitration controls." title="No dispute selected" />
            ) : null}
            {detail.data ? (
              <div className="backoffice-detail-stack">
                <KeyValueList
                  items={[
                    ["Dispute ID", detail.data.dispute.id],
                    ["Payment intent", detail.data.dispute.paymentIntentId],
                    ["Merchant", detail.data.dispute.merchantId],
                    ["Cardholder", detail.data.dispute.cardholderUserId],
                    ["Amount", formatAmount(detail.data.dispute.amount, detail.data.dispute.currency)],
                    ["Reason", detail.data.dispute.reasonCode],
                    ["Narrative", detail.data.dispute.narrative ?? "—"],
                    ["Merchant deadline", formatDateTime(detail.data.dispute.merchantResponseDeadline)],
                    ["Provisional journal", detail.data.dispute.provisionalCreditJournalId ?? "—"],
                    ["Created", formatDateTime(detail.data.dispute.createdAt)],
                    ["Evidence submission", detail.data.evidenceSubmission?.id ?? "—"],
                    ["Evidence state", detail.data.evidenceSubmission?.state ?? "—"],
                    ["Attachments", detail.data.evidenceSubmission ? String(detail.data.evidenceSubmission.attachments.length) : "0"],
                  ]}
                />
                <Field label="Outcome">
                  <Select onChange={(event) => setOutcome(event.target.value)} value={outcome}>
                    <option value="WON">WON</option>
                    <option value="LOST">LOST</option>
                  </Select>
                </Field>
                <Field hint="20–4000 characters required." label="Rationale">
                  <TextArea onChange={(event) => setRationale(event.target.value)} rows={5} value={rationale} />
                </Field>
                {detail.data.dispute.state !== "EVIDENCE_SUBMITTED" ? (
                  <div className="backoffice-inline-note">Arbitration is available only when the dispute is in EVIDENCE_SUBMITTED state.</div>
                ) : null}
                {actionError ? <PageError message={actionError} /> : null}
                <Toolbar>
                  <Button disabled={submitting || detail.data.dispute.state !== "EVIDENCE_SUBMITTED"} onClick={handleSubmit} variant="primary">
                    {submitting ? "Submitting..." : "Record arbitration"}
                  </Button>
                </Toolbar>
              </div>
            ) : null}
          </Panel>
        }
        listPanel={
          <Panel
            actions={
              <Toolbar>
                <Badge tone="info">{data.length} disputes</Badge>
                <ToolbarSpacer />
                <Button size="sm" variant="secondary" onClick={reload}>
                  Refresh
                </Button>
              </Toolbar>
            }
            title="Chargeback disputes"
          >
            {data.length >= 25 ? (
              <div className="backoffice-inline-note">Showing first 25 disputes. Use the backoffice API to page further.</div>
            ) : null}
            {loading ? <div className="backoffice-loading-copy">Loading chargeback disputes...</div> : null}
            {loadError ? <PageError message={loadError} /> : null}
            {!loading ? (
              <DataTable
                columns={[
                  { key: "id", header: "Dispute", className: "mono", render: (row) => row.id.slice(0, 8) + "…" },
                  { key: "state", header: "State", render: (row) => <Badge tone={statusTone(row.state)}>{row.state}</Badge> },
                  { key: "amount", header: "Amount", render: (row) => formatAmount(row.amount, row.currency) },
                  { key: "reason", header: "Reason", render: (row) => row.reasonCode },
                  { key: "created", header: "Created", render: (row) => formatDateTime(row.createdAt) },
                  {
                    key: "action",
                    header: "Action",
                    render: (row) => (
                      <Button size="sm" variant={row.id === selectedId ? "primary" : "ghost"} onClick={() => setSelectedId(row.id)}>
                        Review
                      </Button>
                    ),
                  },
                ]}
                emptyState={<EmptyState body="No chargeback disputes exist yet." title="Queue is empty" />}
                rowKey={(row) => row.id}
                rows={data}
              />
            ) : null}
          </Panel>
        }
      />
    </PageSection>
  );
}

function AuditLogPage() {
  const { session } = useBackofficeApp();
  const token = session!.accessToken;
  const [stream, setStream] = React.useState("ALL");
  const loadAuditFeed = React.useCallback(() => backofficeApi.listAuditFeed(token, stream), [token, stream]);
  const { data, loading, error: feedError, reload } = useRemoteData(async () => (await loadAuditFeed()).items, [] as BackofficeAuditFeedItem[]);
  const [selectedEntryId, setSelectedEntryId] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (data.length === 0) {
      setSelectedEntryId(null);
      return;
    }
    setSelectedEntryId((current) => (current && data.some((item) => item.entryId === current) ? current : data[0].entryId));
  }, [data]);

  const selectedEntry = React.useMemo(
    () => data.find((item) => item.entryId === selectedEntryId) ?? null,
    [data, selectedEntryId],
  );

  return (
    <PageSection
      screenId="BOF-UI-09"
      subtitle="Browse the operational and read-audit log streams."
      title="Audit log"
    >
      <TwoColumnPage
        detailPanel={
          <Panel
            actions={<Badge tone={selectedEntry ? statusTone(selectedEntry.result) : "neutral"}>{selectedEntry?.result ?? "NO_SELECTION"}</Badge>}
            title="Entry detail"
          >
            {!selectedEntry ? <EmptyState body="Select an audit entry to inspect actor, subject, metadata, and correlation fields." title="No entry selected" /> : null}
            {selectedEntry ? (
              <div className="backoffice-detail-stack">
                <KeyValueList
                  items={[
                    ["Entry ID", selectedEntry.entryId],
                    ["Stream", selectedEntry.stream],
                    ["Code", selectedEntry.code],
                    ["Result", selectedEntry.result],
                    ["Actor type", selectedEntry.actorType],
                    ["Actor ID", selectedEntry.actorId ?? "—"],
                    ["Actor ref", selectedEntry.actorReference ?? "—"],
                    ["Subject type", selectedEntry.subjectType],
                    ["Subject ID", selectedEntry.subjectId ?? "—"],
                    ["Resource type", selectedEntry.resourceType ?? "—"],
                    ["Resource ID", selectedEntry.resourceId ?? "—"],
                    ["Request ID", selectedEntry.requestId ?? "—"],
                    ["Correlation ID", selectedEntry.correlationId ?? "—"],
                    ["Created", formatDateTime(selectedEntry.createdAt)],
                    ["Metadata", <pre className="mono" style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-all" }}>{(() => { try { return JSON.stringify(JSON.parse(selectedEntry.metadataJson), null, 2); } catch { return selectedEntry.metadataJson; } })()}</pre>],
                  ]}
                />
              </div>
            ) : null}
          </Panel>
        }
        listPanel={
          <Panel
            actions={
              <Toolbar>
                <Badge tone="info">{data.length} rows</Badge>
                <Select onChange={(event) => setStream(event.target.value)} value={stream}>
                  <option value="ALL">ALL</option>
                  <option value="AUDIT_LOG">AUDIT_LOG</option>
                  <option value="READ_AUDIT_LOG">READ_AUDIT_LOG</option>
                </Select>
                <ToolbarSpacer />
                <Button size="sm" variant="secondary" onClick={reload}>
                  Refresh
                </Button>
              </Toolbar>
            }
            title="Audit feed"
          >
            {data.length >= 50 ? (
              <div className="backoffice-inline-note">Showing first 50 entries. Change the stream filter to narrow the view.</div>
            ) : null}
            {loading ? <div className="backoffice-loading-copy">Loading audit feed...</div> : null}
            {feedError ? <PageError message={feedError} /> : null}
            {!loading ? (
              <DataTable
                columns={[
                  { key: "id", header: "Entry", className: "mono", render: (row) => row.entryId.slice(0, 8) + "…" },
                  { key: "stream", header: "Stream", render: (row) => row.stream },
                  { key: "code", header: "Code", className: "mono", render: (row) => row.code },
                  { key: "actor", header: "Actor", render: (row) => row.actorType },
                  { key: "result", header: "Result", render: (row) => <Badge tone={statusTone(row.result)}>{row.result}</Badge> },
                  { key: "created", header: "Created", render: (row) => formatDateTime(row.createdAt) },
                  {
                    key: "action",
                    header: "Action",
                    render: (row) => (
                      <Button size="sm" variant={row.entryId === selectedEntryId ? "primary" : "ghost"} onClick={() => setSelectedEntryId(row.entryId)}>
                        View
                      </Button>
                    ),
                  },
                ]}
                emptyState={<EmptyState body="No audit rows matched the current filter." title="Audit feed empty" />}
                rowKey={(row) => row.entryId}
                rows={data}
              />
            ) : null}
          </Panel>
        }
      />
    </PageSection>
  );
}

function ActorControlsPage() {
  const { session } = useBackofficeApp();
  const token = session!.accessToken;
  const [resourceId, setResourceId] = React.useState("");
  const [probeLoading, setProbeLoading] = React.useState(false);
  const [probeError, setProbeError] = React.useState<string | null>(null);
  const [probeResult, setProbeResult] = React.useState<ReadAuditProbeResponse | null>(null);
  const [controlInput, setControlInput] = React.useState<ActorControlRequest>({
    actorType: "END_USER",
    actorId: "",
    state: "FROZEN",
    reasonCode: "manual_backoffice_action",
  });
  const [controlLoading, setControlLoading] = React.useState(false);
  const [controlError, setControlError] = React.useState<string | null>(null);
  const [controlResult, setControlResult] = React.useState<ActorControlResponse | null>(null);

  async function runProbe(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setProbeLoading(true);
    setProbeError(null);
    try {
      const response = await backofficeApi.runReadAuditProbe(token, resourceId.trim());
      setProbeResult(response);
    } catch (probeRequestError) {
      setProbeResult(null);
      setProbeError(formatError(probeRequestError));
    } finally {
      setProbeLoading(false);
    }
  }

  async function applyControl(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setControlLoading(true);
    setControlError(null);
    try {
      const response = await backofficeApi.setActorControl(token, {
        ...controlInput,
        actorId: controlInput.actorId.trim(),
        reasonCode: controlInput.reasonCode.trim(),
      });
      setControlResult(response);
    } catch (controlRequestError) {
      setControlResult(null);
      setControlError(formatError(controlRequestError));
    } finally {
      setControlLoading(false);
    }
  }

  return (
    <PageSection
      screenId="BOF-UI-10"
      subtitle="Freeze or unfreeze end-user and merchant accounts. Use the read-audit probe to verify that a resource access has been logged."
      title="Actor controls"
    >
      <div className="backoffice-page-grid">
        <Panel title="Read-audit probe">
          <form className="backoffice-form" onSubmit={runProbe}>
            <Field hint="Must be a valid UUID." label="Resource ID">
              <Input onChange={(event) => setResourceId(event.target.value)} value={resourceId} />
            </Field>
            {probeError ? <PageError message={probeError} /> : null}
            <Button disabled={probeLoading} type="submit" variant="primary">
              {probeLoading ? "Running..." : "Run probe"}
            </Button>
          </form>
          {probeResult ? (
            <div className="backoffice-result-card">
              <KeyValueList
                items={[
                  ["Resource ID", probeResult.resourceId],
                  ["Resource type", probeResult.resourceType],
                  ["Read-audited", probeResult.readAudited ? "true" : "false"],
                ]}
              />
            </div>
          ) : null}
        </Panel>
        <Panel title="Account freeze / unfreeze">
          <form className="backoffice-form" onSubmit={applyControl}>
            <Field label="Actor type">
              <Select
                onChange={(event) =>
                  setControlInput((current) => ({
                    ...current,
                    actorType: event.target.value,
                  }))
                }
                value={controlInput.actorType}
              >
                <option value="END_USER">END_USER</option>
                <option value="MERCHANT">MERCHANT</option>
              </Select>
            </Field>
            <Field hint="Actor UUID from the backoffice record." label="Actor ID">
              <Input
                onChange={(event) =>
                  setControlInput((current) => ({
                    ...current,
                    actorId: event.target.value,
                  }))
                }
                value={controlInput.actorId}
              />
            </Field>
            <Field label="New state">
              <Select
                onChange={(event) =>
                  setControlInput((current) => ({
                    ...current,
                    state: event.target.value,
                  }))
                }
                value={controlInput.state}
              >
                <option value="FROZEN">FROZEN</option>
                <option value="ACTIVE">ACTIVE</option>
              </Select>
            </Field>
            <Field label="Reason code">
              <Input
                onChange={(event) =>
                  setControlInput((current) => ({
                    ...current,
                    reasonCode: event.target.value,
                  }))
                }
                value={controlInput.reasonCode}
              />
            </Field>
            {controlError ? <PageError message={controlError} /> : null}
            <Button disabled={controlLoading} type="submit" variant="primary">
              {controlLoading ? "Applying..." : "Apply control"}
            </Button>
          </form>
          {controlResult ? (
            <div className="backoffice-result-card">
              <KeyValueList
                items={[
                  ["Actor type", controlResult.actorType],
                  ["Actor ID", controlResult.actorId],
                  ["State", controlResult.state],
                  ["Reason code", controlResult.reasonCode],
                ]}
              />
            </div>
          ) : null}
        </Panel>
      </div>
    </PageSection>
  );
}

function TwoColumnPage({
  listPanel,
  detailPanel,
}: {
  listPanel: React.ReactNode;
  detailPanel: React.ReactNode;
}) {
  return <div className="backoffice-two-column">{listPanel}{detailPanel}</div>;
}

function KeyValueList({ items }: { items: Array<[string, React.ReactNode]> }) {
  return (
    <div className="backoffice-kv-grid">
      {items.map(([label, value]) => (
        <div className="backoffice-kv-row" key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  );
}

function AuditRail() {
  const { session } = useBackofficeApp();
  const profile = session?.profile;

  return (
    <div className="backoffice-rail">
      <Panel title="Session">
        <div className="backoffice-rail-list">
          <div>
            <strong>Roles</strong>
            <span>{profile ? profile.roles.join(", ") : "—"}</span>
          </div>
          <div>
            <strong>Subject</strong>
            <span>{profile?.subject ?? "No active subject"}</span>
          </div>
        </div>
      </Panel>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BackofficeRoot />
  </React.StrictMode>,
);
