import { createPlatformApiClient, PlatformApiError } from "@minifin/api-client";

const platformClient = createPlatformApiClient({ baseUrl: "" });

export { PlatformApiError };

export interface BackofficeMeResponse {
  subject: string;
  email: string;
  roles: string[];
  issuer: string;
}

export interface ManualDeposit {
  depositId: string;
  userId: string;
  amount: string;
  currency: string;
  state: string;
  reason?: string | null;
  journalEntryId?: string | null;
  sourceOfFundsRequired: boolean;
  sourceOfFundsSubmitted: boolean;
  createdAt: string;
  decidedAt?: string | null;
}

export interface ManualWithdrawal {
  withdrawalId: string;
  userId: string;
  amount: string;
  currency: string;
  state: string;
  reason?: string | null;
  holdJournalEntryId?: string | null;
  completionJournalEntryId?: string | null;
  releaseJournalEntryId?: string | null;
  createdAt: string;
  heldAt?: string | null;
  decidedAt?: string | null;
}

export interface KycCase {
  id: string;
  endUserId: string;
  status: string;
  vendor: string;
  vendorApplicantId?: string | null;
  levelName?: string | null;
  externalUserId: string;
  reviewAnswer?: string | null;
  reviewRejectType?: string | null;
  reviewModerationComment?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface KycDecisionResponse {
  caseId: string;
  previousStatus: string;
  status: string;
  decision: string;
  decidedBySubject: string;
  decidedByRole?: string | null;
  decidedAt: string;
}

export interface AmlAlert {
  id: string;
  endUserId: string;
  ruleCode: string;
  severity: string;
  status: string;
  windowStartedAt: string;
  windowEndedAt: string;
  observedCount: number;
  thresholdCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AmlDecisionResponse {
  alertId: string;
  endUserId: string;
  previousStatus: string;
  status: string;
  decision: string;
  decidedBySubject: string;
  decidedByRole?: string | null;
  unfrozeActor: boolean;
  decidedAt: string;
}

export interface SanctionsHit {
  id: string;
  endUserId: string;
  kycProfileId?: string | null;
  status: string;
  reason: string;
  vendor: string;
  matchScore?: string | number | null;
  matchedEntityId?: string | null;
  matchedName?: string | null;
  requestId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SanctionsDecisionResponse {
  hitId: string;
  previousStatus: string;
  status: string;
  decision: string;
  exceptionId: string;
  decidedBySubject: string;
  decidedByRole?: string | null;
  decidedAt: string;
}

export interface ReadAuditProbeResponse {
  resourceId: string;
  resourceType: string;
  readAudited: boolean;
}

export interface ActorControlRequest {
  actorType: string;
  actorId: string;
  state: string;
  reasonCode: string;
}

export interface ActorControlResponse {
  actorType: string;
  actorId: string;
  state: string;
  reasonCode: string;
}

export interface ArbitrationDecisionResponse {
  disputeId: string;
  state: string;
  outcome: string;
  arbitrationJournalId: string;
}

export interface ChargebackDispute {
  id: string;
  paymentIntentId: string;
  merchantId: string;
  cardholderUserId: string;
  amount: string;
  currency: string;
  reasonCode: string;
  narrative?: string | null;
  state: string;
  merchantResponseDeadline: string;
  provisionalCreditJournalId?: string | null;
  createdAt: string;
}

export interface ChargebackEvidenceAttachment {
  id: string;
  fileName: string;
  contentType: string;
  storageKey: string;
  sizeBytes: number;
}

export interface ChargebackEvidenceSubmission {
  id: string;
  disputeId: string;
  state: string;
  narrative: string;
  attachments: ChargebackEvidenceAttachment[];
  createdAt: string;
}

export interface BackofficeChargebackDetail {
  dispute: ChargebackDispute;
  evidenceSubmission?: ChargebackEvidenceSubmission | null;
}

export interface BackofficeAuditFeedItem {
  entryId: string;
  stream: string;
  code: string;
  actorType: string;
  actorId?: string | null;
  actorReference?: string | null;
  subjectType: string;
  subjectId?: string | null;
  resourceType?: string | null;
  resourceId?: string | null;
  result: string;
  metadataJson: string;
  requestId?: string | null;
  correlationId?: string | null;
  createdAt: string;
}

export const backofficeApi = {
  me(token: string) {
    return platformClient.backoffice.get<BackofficeMeResponse>("/api/v1/backoffice/me", token);
  },
  listManualDeposits(token: string) {
    return platformClient.backoffice.get<ManualDeposit[]>("/api/v1/backoffice/manual-ops/deposits", token);
  },
  decideManualDeposit(token: string, depositId: string, decision: string, reason: string) {
    return platformClient.backoffice.post<ManualDeposit>(
      `/api/v1/backoffice/manual-ops/deposits/${depositId}/decision`,
      token,
      { decision, reason },
    );
  },
  listManualWithdrawals(token: string) {
    return platformClient.backoffice.get<ManualWithdrawal[]>("/api/v1/backoffice/manual-ops/withdrawals", token);
  },
  decideManualWithdrawal(token: string, withdrawalId: string, decision: string, reason: string) {
    return platformClient.backoffice.post<ManualWithdrawal>(
      `/api/v1/backoffice/manual-ops/withdrawals/${withdrawalId}/decision`,
      token,
      { decision, reason },
    );
  },
  listKycCases(token: string) {
    return platformClient.backoffice.get<KycCase[]>("/api/v1/backoffice/kyc-cases", token);
  },
  getKycCase(token: string, caseId: string) {
    return platformClient.backoffice.get<KycCase>(`/api/v1/backoffice/kyc-cases/${caseId}`, token);
  },
  decideKycCase(token: string, caseId: string, decision: string, rationale: string) {
    return platformClient.backoffice.post<KycDecisionResponse>(
      `/api/v1/backoffice/kyc-cases/${caseId}/decision`,
      token,
      { decision, rationale },
    );
  },
  listAmlAlerts(token: string) {
    return platformClient.backoffice.get<AmlAlert[]>("/api/v1/backoffice/aml-alerts", token);
  },
  getAmlAlert(token: string, alertId: string) {
    return platformClient.backoffice.get<AmlAlert>(`/api/v1/backoffice/aml-alerts/${alertId}`, token);
  },
  decideAmlAlert(token: string, alertId: string, decision: string, rationale: string) {
    return platformClient.backoffice.post<AmlDecisionResponse>(
      `/api/v1/backoffice/aml-alerts/${alertId}/decision`,
      token,
      { decision, rationale },
    );
  },
  listSanctionsHits(token: string) {
    return platformClient.backoffice.get<SanctionsHit[]>("/api/v1/backoffice/sanctions-hits", token);
  },
  getSanctionsHit(token: string, hitId: string) {
    return platformClient.backoffice.get<SanctionsHit>(`/api/v1/backoffice/sanctions-hits/${hitId}`, token);
  },
  decideSanctionsHit(token: string, hitId: string, decision: string, rationale: string) {
    return platformClient.backoffice.post<SanctionsDecisionResponse>(
      `/api/v1/backoffice/sanctions-hits/${hitId}/decision`,
      token,
      { decision, rationale },
    );
  },
  runReadAuditProbe(token: string, resourceId: string) {
    return platformClient.backoffice.get<ReadAuditProbeResponse>(
      `/api/v1/backoffice/read-audit/probe/${resourceId}`,
      token,
    );
  },
  setActorControl(token: string, input: ActorControlRequest) {
    return platformClient.backoffice.post<ActorControlResponse>(
      "/api/v1/backoffice/actor-controls",
      token,
      input,
    );
  },
  listChargebacks(token: string, limit = 25) {
    return platformClient.backoffice.get<{ items: ChargebackDispute[] }>(
      `/api/v1/backoffice/disputes?limit=${limit}`,
      token,
    );
  },
  getChargeback(token: string, disputeId: string) {
    return platformClient.backoffice.get<BackofficeChargebackDetail>(
      `/api/v1/backoffice/disputes/${disputeId}`,
      token,
    );
  },
  decideChargeback(token: string, disputeId: string, outcome: string, rationale: string) {
    return platformClient.backoffice.post<ArbitrationDecisionResponse>(
      `/api/v1/backoffice/disputes/${disputeId}/arbitration`,
      token,
      { outcome, rationale },
    );
  },
  listAuditFeed(token: string, stream = "ALL", limit = 50) {
    return platformClient.backoffice.get<{ items: BackofficeAuditFeedItem[] }>(
      `/api/v1/backoffice/audit-log?stream=${encodeURIComponent(stream)}&limit=${limit}`,
      token,
    );
  },
};
