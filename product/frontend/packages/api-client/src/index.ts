export type RuntimeService = "platform" | "acquirer" | "network" | "issuer" | "vault";

export interface HealthProbeTarget {
  service: RuntimeService;
  url: string;
}
