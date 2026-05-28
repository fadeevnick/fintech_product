import React from "react";

export type SurfaceKind = "enduser" | "merchant" | "backoffice";
export type BadgeTone =
  | "neutral"
  | "info"
  | "success"
  | "warning"
  | "danger"
  | "accent";
export type NavItemTone = "default" | "info" | "warning";

export interface SurfaceTheme {
  label: string;
  workspaceLabel: string;
  environmentLabel: string;
}

export const surfaceLabels: Record<SurfaceKind, string> = {
  enduser: "End-user wallet",
  merchant: "Merchant dashboard",
  backoffice: "Backoffice operations",
};

export const surfaceThemes: Record<SurfaceKind, SurfaceTheme> = {
  enduser: {
    label: surfaceLabels.enduser,
    workspaceLabel: "Wallet workspace",
    environmentLabel: "LOCAL ENDUSER",
  },
  merchant: {
    label: surfaceLabels.merchant,
    workspaceLabel: "Merchant workspace",
    environmentLabel: "LOCAL MERCHANT",
  },
  backoffice: {
    label: surfaceLabels.backoffice,
    workspaceLabel: "Backoffice workspace",
    environmentLabel: "LOCAL BACKOFFICE",
  },
};

export function cx(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(" ");
}

export interface AppShellProps {
  surface: SurfaceKind;
  sidebar: React.ReactNode;
  header?: React.ReactNode;
  aside?: React.ReactNode;
  children: React.ReactNode;
}

export function AppShell({ surface, sidebar, header, aside, children }: AppShellProps) {
  return (
    <div className={cx("mfp-shell", `mfp-shell-${surface}`)}>
      {sidebar}
      <div className="mfp-main">
        {header}
        <div className={cx("mfp-main-body", Boolean(aside) && "has-aside")}>
          <main className="mfp-main-content">{children}</main>
          {aside ? <aside className="mfp-main-aside">{aside}</aside> : null}
        </div>
      </div>
    </div>
  );
}

interface AppSidebarProps {
  surface: SurfaceKind;
  sections: Array<{
    title: string;
    items: React.ReactNode[];
  }>;
  userName?: string;
  userMeta?: string;
  productName?: string;
  environmentLabel?: string;
}

export function AppSidebar({
  surface,
  sections,
  userName,
  userMeta,
  productName = "MiniFin",
  environmentLabel,
}: AppSidebarProps) {
  const theme = surfaceThemes[surface];

  return (
    <aside className="mfp-sidebar">
      <div className="mfp-sidebar-brand">
        <div className="mfp-sidebar-product">{productName}</div>
        <div className="mfp-sidebar-surface">{theme.workspaceLabel}</div>
      </div>
      <div className="mfp-sidebar-env">{environmentLabel ?? theme.environmentLabel}</div>
      <nav className="mfp-sidebar-nav">
        {sections.map((section) => (
          <section className="mfp-sidebar-section" key={section.title}>
            <div className="mfp-sidebar-section-title">{section.title}</div>
            <div className="mfp-sidebar-section-items">{section.items}</div>
          </section>
        ))}
      </nav>
      {userName ? (
        <div className="mfp-sidebar-user">
          <div className="mfp-sidebar-user-name">{userName}</div>
          {userMeta ? <div className="mfp-sidebar-user-meta">{userMeta}</div> : null}
        </div>
      ) : null}
    </aside>
  );
}

interface AppSidebarItemProps {
  label: string;
  icon?: React.ReactNode;
  meta?: React.ReactNode;
  active?: boolean;
  disabled?: boolean;
  tone?: NavItemTone;
  onClick?: () => void;
}

export function AppSidebarItem({
  label,
  icon,
  meta,
  active = false,
  disabled = false,
  tone = "default",
  onClick,
}: AppSidebarItemProps) {
  return (
    <button
      className={cx(
        "mfp-sidebar-item",
        active && "is-active",
        disabled && "is-disabled",
        tone !== "default" && `tone-${tone}`,
      )}
      type="button"
      disabled={disabled}
      onClick={onClick}
    >
      {icon ? <span className="mfp-sidebar-item-icon">{icon}</span> : null}
      <span className="mfp-sidebar-item-label">{label}</span>
      {meta ? <span className="mfp-sidebar-item-meta">{meta}</span> : null}
    </button>
  );
}

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  breadcrumbs?: React.ReactNode;
  screenId?: string;
  rightSlot?: React.ReactNode;
  children?: React.ReactNode;
}

export function PageHeader({
  title,
  subtitle,
  breadcrumbs,
  screenId,
  rightSlot,
  children,
}: PageHeaderProps) {
  return (
    <header className="mfp-page-header">
      <div className="mfp-page-header-top">
        <div className="mfp-page-header-copy">
          {breadcrumbs ? <div className="mfp-page-breadcrumbs">{breadcrumbs}</div> : null}
          <div className="mfp-page-title-row">
            <h1>{title}</h1>
            {subtitle ? <p>{subtitle}</p> : null}
          </div>
        </div>
        <div className="mfp-page-header-meta">
          {screenId ? <span className="mfp-screen-id">{screenId}</span> : null}
          {rightSlot}
        </div>
      </div>
      {children ? <div className="mfp-page-header-bottom">{children}</div> : null}
    </header>
  );
}

interface PanelProps {
  title?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
}

export function Panel({ title, actions, children }: PanelProps) {
  return (
    <section className="mfp-panel">
      {title || actions ? (
        <div className="mfp-panel-header">
          {title ? <div className="mfp-panel-title">{title}</div> : <div />}
          {actions}
        </div>
      ) : null}
      <div className="mfp-panel-body">{children}</div>
    </section>
  );
}

interface ToolbarProps {
  children: React.ReactNode;
}

export function Toolbar({ children }: ToolbarProps) {
  return <div className="mfp-toolbar">{children}</div>;
}

export function ToolbarSpacer() {
  return <div className="mfp-toolbar-spacer" />;
}

interface ButtonCommonProps {
  children: React.ReactNode;
  variant?: "primary" | "secondary" | "danger" | "ghost";
  size?: "sm" | "md";
}

export type ButtonProps = ButtonCommonProps &
  Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "children">;

export function Button({
  children,
  className,
  variant = "secondary",
  size = "md",
  ...props
}: ButtonProps) {
  return (
    <button
      className={cx("mfp-button", `variant-${variant}`, `size-${size}`, className)}
      {...props}
      type={props.type ?? "button"}
    >
      {children}
    </button>
  );
}

interface BadgeProps {
  children: React.ReactNode;
  tone?: BadgeTone;
}

export function Badge({ children, tone = "neutral" }: BadgeProps) {
  return <span className={cx("mfp-badge", `tone-${tone}`)}>{children}</span>;
}

interface StatGridProps {
  columns?: 2 | 3 | 4 | 5 | 6;
  children: React.ReactNode;
}

export function StatGrid({ columns = 4, children }: StatGridProps) {
  return <div className={cx("mfp-stat-grid", `cols-${columns}`)}>{children}</div>;
}

interface StatCardProps {
  label: string;
  value: React.ReactNode;
  tone?: BadgeTone;
  meta?: string;
}

export function StatCard({ label, value, tone = "neutral", meta }: StatCardProps) {
  return (
    <div className="mfp-stat-card">
      <div className={cx("mfp-stat-value", `tone-${tone}`)}>{value}</div>
      <div className="mfp-stat-label">{label}</div>
      {meta ? <div className="mfp-stat-meta">{meta}</div> : null}
    </div>
  );
}

export interface ModeTab {
  key: string;
  label: string;
}

interface ModeTabsProps {
  tabs: ModeTab[];
  activeKey: string;
  onChange: (key: string) => void;
}

export function ModeTabs({ tabs, activeKey, onChange }: ModeTabsProps) {
  return (
    <div className="mfp-mode-tabs">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          className={cx("mfp-mode-tab", tab.key === activeKey && "is-active")}
          onClick={() => onChange(tab.key)}
          type="button"
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

interface EmptyStateProps {
  title: string;
  body: string;
  action?: React.ReactNode;
}

export function EmptyState({ title, body, action }: EmptyStateProps) {
  return (
    <div className="mfp-empty-state">
      <strong>{title}</strong>
      <p>{body}</p>
      {action}
    </div>
  );
}

interface FieldProps {
  label: string;
  hint?: string;
  children: React.ReactNode;
}

export function Field({ label, hint, children }: FieldProps) {
  return (
    <label className="mfp-field">
      <span className="mfp-field-label">{label}</span>
      {children}
      {hint ? <span className="mfp-field-hint">{hint}</span> : null}
    </label>
  );
}

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cx("mfp-input", props.className)} {...props} />;
}

export type SelectProps = React.SelectHTMLAttributes<HTMLSelectElement>;

export function Select(props: SelectProps) {
  return <select className={cx("mfp-input", props.className)} {...props} />;
}

export function TextArea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cx("mfp-input", "mfp-textarea", props.className)} {...props} />;
}

export interface DataTableColumn<Row> {
  key: string;
  header: string;
  render: (row: Row) => React.ReactNode;
  className?: string;
}

export interface DataTableProps<Row> {
  columns: Array<DataTableColumn<Row>>;
  rows: Row[];
  rowKey: (row: Row) => string;
  emptyState?: React.ReactNode;
}

export function DataTable<Row>({
  columns,
  rows,
  rowKey,
  emptyState,
}: DataTableProps<Row>) {
  if (rows.length === 0) {
    return emptyState ? <>{emptyState}</> : null;
  }

  return (
    <div className="mfp-table-wrap">
      <table className="mfp-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((column) => (
                <td className={column.className} key={column.key}>
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
