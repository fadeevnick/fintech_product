import { backofficeApi, type BackofficeMeResponse } from "./backofficeApi";

const BACKOFFICE_SESSION_KEY = "minifin.backoffice.session";
const BACKOFFICE_REALM = "minifin-backoffice";
const BACKOFFICE_CLIENT_ID = "minifin-backoffice-local";

export interface BackofficeSession {
  accessToken: string;
  profile: BackofficeMeResponse;
}

interface KeycloakTokenResponse {
  access_token?: string;
  error?: string;
  error_description?: string;
}

export function loadBackofficeSession(): BackofficeSession | null {
  const raw = window.localStorage.getItem(BACKOFFICE_SESSION_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as BackofficeSession;
    if (!parsed.accessToken || !parsed.profile?.subject) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function persistBackofficeSession(session: BackofficeSession) {
  window.localStorage.setItem(BACKOFFICE_SESSION_KEY, JSON.stringify(session));
}

export function clearBackofficeSession() {
  window.localStorage.removeItem(BACKOFFICE_SESSION_KEY);
}

export async function loginBackoffice(username: string, password: string): Promise<BackofficeSession> {
  const form = new URLSearchParams({
    client_id: BACKOFFICE_CLIENT_ID,
    grant_type: "password",
    username,
    password,
  });

  const response = await fetch(`/realms/${BACKOFFICE_REALM}/protocol/openid-connect/token`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: form.toString(),
  });

  const rawText = await response.text();
  const payload = rawText ? (JSON.parse(rawText) as KeycloakTokenResponse) : {};

  if (!response.ok || !payload.access_token) {
    throw new Error(payload.error_description ?? payload.error ?? "Backoffice login failed.");
  }

  const profile = await backofficeApi.me(payload.access_token);
  const session = {
    accessToken: payload.access_token,
    profile,
  };
  persistBackofficeSession(session);
  return session;
}

export async function refreshBackofficeSession(accessToken: string): Promise<BackofficeSession> {
  const profile = await backofficeApi.me(accessToken);
  const session = { accessToken, profile };
  persistBackofficeSession(session);
  return session;
}
