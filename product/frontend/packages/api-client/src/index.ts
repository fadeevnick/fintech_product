export type RuntimeService = "platform" | "acquirer" | "network" | "issuer" | "vault";

export interface HealthProbeTarget {
  service: RuntimeService;
  url: string;
}

export interface ApiError {
  code: string;
  message: string;
  field?: string | null;
  hint?: string | null;
}

export interface ApiEnvelope<T> {
  data: T | null;
  errors: ApiError[];
}

export class PlatformApiError extends Error {
  readonly status: number;
  readonly errors: ApiError[];

  constructor(status: number, errors: ApiError[]) {
    super(errors[0]?.message ?? `Platform API request failed with status ${status}.`);
    this.name = "PlatformApiError";
    this.status = status;
    this.errors = errors;
  }
}

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface MerchantRegisterRequest extends RegisterRequest {
  companyName: string;
  country: string;
  businessType: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterResponse {
  userId: string;
  email: string;
  status: string;
  verificationToken?: string | null;
}

export interface MerchantRegisterResponse {
  merchantId: string;
  employeeId: string;
  email: string;
  role: string;
  employeeStatus: string;
  merchantStatus: string;
  verificationToken?: string | null;
}

export interface VerifyEmailResponse {
  userId: string;
  email: string;
  status: string;
}

export interface MerchantVerifyEmailResponse {
  merchantId: string;
  employeeId: string;
  email: string;
  role: string;
  employeeStatus: string;
  merchantStatus: string;
}

export interface LoginResponse {
  userId: string;
  email: string;
  status: string;
}

export interface MerchantLoginResponse {
  merchantId: string;
  employeeId: string;
  email: string;
  role: string;
  employeeStatus: string;
  merchantStatus: string;
}

export interface MeResponse {
  userId: string;
  email: string;
  status: string;
}

export interface MerchantMeResponse {
  merchantId: string;
  employeeId: string;
  email: string;
  role: string;
  employeeStatus: string;
  merchantStatus: string;
}

export interface LogoutResponse {
  loggedOut: boolean;
}

export interface CardMetadataResponse {
  id: string;
  state: string;
  last4: string;
  expirationMonth: number;
  expirationYear: number;
  bin: string;
}

export interface CardIssueResponse {
  card: CardMetadataResponse;
}

export interface CardListResponse {
  items: CardMetadataResponse[];
}

export interface EvidenceAttachmentRequest {
  fileName?: string | null;
  contentType?: string | null;
  storageKey?: string | null;
  sizeBytes?: number | null;
  contentBase64?: string | null;
}

export interface SubmitEvidenceRequest {
  narrative?: string | null;
  attachments?: EvidenceAttachmentRequest[];
}

export interface EvidenceAttachmentDto {
  id: string;
  fileName: string;
  contentType: string;
  storageKey: string;
  sizeBytes: number;
}

export interface EvidenceSubmissionDto {
  id: string;
  disputeId: string;
  state: string;
  narrative: string;
  attachments: EvidenceAttachmentDto[];
  createdAt: string;
}

export interface ChargebackDisputeDto {
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

export interface ChargebackDisputeListResponse {
  items: ChargebackDisputeDto[];
}

export interface MerchantDisputeDetailDto {
  dispute: ChargebackDisputeDto;
  evidenceSubmission?: EvidenceSubmissionDto | null;
}

export interface MerchantAcceptChargebackDto {
  disputeId: string;
  state: string;
  externalState: string;
  merchantDebitJournalId: string;
}

export interface MerchantSettlementBatchSummaryDto {
  batchId: string;
  status: string;
  currency: string;
  itemCount: number;
  grossAmount: string;
  merchantNetAmount: string;
  interchangeAmount: string;
  networkAssessmentAmount: string;
  acquirerMarginAmount: string;
  settledAt: string;
  createdAt: string;
}

export interface MerchantSettlementBatchListResponse {
  items: MerchantSettlementBatchSummaryDto[];
}

export interface MerchantSettlementItemDto {
  settlementItemId: string;
  paymentIntentId: string;
  grossAmount: string;
  merchantNetAmount: string;
  interchangeAmount: string;
  networkAssessmentAmount: string;
  acquirerMarginAmount: string;
  currency: string;
  status: string;
  ledgerJournalId: string;
  createdAt: string;
}

export interface MerchantSettlementBatchDetailDto {
  batch: MerchantSettlementBatchSummaryDto;
  items: MerchantSettlementItemDto[];
}

export interface PlatformApiClientOptions {
  baseUrl: string;
  fetchFn?: typeof fetch;
}

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
}

export function createPlatformApiClient({
  baseUrl,
  fetchFn = fetch,
}: PlatformApiClientOptions) {
  const normalizedBaseUrl = baseUrl.replace(/\/$/, "");

  async function request<T>(path: string, init: RequestOptions = {}): Promise<T> {
    const headers = new Headers(init.headers);
    let body = init.body;

    if (body !== undefined && body !== null && !(body instanceof FormData)) {
      headers.set("Content-Type", "application/json");
      body = JSON.stringify(body);
    }

    const response = await fetchFn(`${normalizedBaseUrl}${path}`, {
      ...init,
      body: body as BodyInit | null | undefined,
      headers,
      credentials: init.credentials ?? "include",
    });

    const text = await response.text();
    const payload = text ? (JSON.parse(text) as ApiEnvelope<T>) : { data: null, errors: [] };

    if (!response.ok || payload.errors.length > 0) {
      throw new PlatformApiError(response.status, payload.errors);
    }

    return payload.data as T;
  }

  return {
    request,
    endUserAuth: {
      register: (input: RegisterRequest) =>
        request<RegisterResponse>("/api/v1/enduser/register", { method: "POST", body: input }),
      verifyEmail: (input: VerifyEmailRequest) =>
        request<VerifyEmailResponse>("/api/v1/enduser/email/verify", { method: "POST", body: input }),
      login: (input: LoginRequest) =>
        request<LoginResponse>("/api/v1/enduser/login", { method: "POST", body: input }),
      me: () => request<MeResponse>("/api/v1/enduser/me"),
      logout: () => request<LogoutResponse>("/api/v1/enduser/logout", { method: "POST" }),
    },
    merchantAuth: {
      register: (input: MerchantRegisterRequest) =>
        request<MerchantRegisterResponse>("/api/v1/merchant/register", { method: "POST", body: input }),
      verifyEmail: (input: VerifyEmailRequest) =>
        request<MerchantVerifyEmailResponse>("/api/v1/merchant/email/verify", { method: "POST", body: input }),
      login: (input: LoginRequest) =>
        request<MerchantLoginResponse>("/api/v1/merchant/login", { method: "POST", body: input }),
      me: () => request<MerchantMeResponse>("/api/v1/merchant/me"),
      logout: () => request<LogoutResponse>("/api/v1/merchant/logout", { method: "POST" }),
    },
    cards: {
      list: () => request<CardListResponse>("/api/v1/cards"),
      get: (cardId: string) => request<CardMetadataResponse>(`/api/v1/cards/${cardId}`),
      issue: () => request<CardIssueResponse>("/api/v1/cards", { method: "POST" }),
    },
    merchantDisputes: {
      list: (limit = 25) =>
        request<ChargebackDisputeListResponse>(`/api/v1/merchant/disputes?limit=${limit}`),
      get: (disputeId: string) =>
        request<MerchantDisputeDetailDto>(`/api/v1/merchant/disputes/${disputeId}`),
      submitEvidence: (disputeId: string, input: SubmitEvidenceRequest) =>
        request<EvidenceSubmissionDto>(`/api/v1/merchant/disputes/${disputeId}/evidence`, {
          method: "POST",
          body: input,
        }),
      accept: (disputeId: string) =>
        request<MerchantAcceptChargebackDto>(`/api/v1/merchant/disputes/${disputeId}/accept`, {
          method: "POST",
        }),
    },
    merchantSettlements: {
      list: (limit = 25) =>
        request<MerchantSettlementBatchListResponse>(`/api/v1/merchant/settlements?limit=${limit}`),
      get: (batchId: string) =>
        request<MerchantSettlementBatchDetailDto>(`/api/v1/merchant/settlements/${batchId}`),
    },
    backoffice: {
      get: <T>(path: string, token: string) =>
        request<T>(path, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }),
      post: <T>(path: string, token: string, input?: unknown) =>
        request<T>(path, {
          method: "POST",
          body: input,
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }),
    },
  };
}
