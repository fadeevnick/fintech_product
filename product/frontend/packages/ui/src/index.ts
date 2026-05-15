export type SurfaceKind = "enduser" | "merchant" | "backoffice";

export const surfaceLabels: Record<SurfaceKind, string> = {
  enduser: "End-user wallet",
  merchant: "Merchant dashboard",
  backoffice: "Backoffice operations",
};
