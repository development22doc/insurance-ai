// API Gateway base URL - browsers communicate through the Gateway only
const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// Lightweight, robust response parser used across the frontend to avoid
// unhandled "Unexpected end of JSON input" errors when servers return
// empty or non-JSON error responses.
export async function parseApiResponse<T>(response: Response): Promise<T> {
  const contentType = response.headers.get('content-type') || '';

  // Read the body ONCE - store as text for parsing
  const text = await response.text();

  // Handle non-2xx responses first and normalize errors.
  if (!response.ok) {
    // Prefer JSON error body when available
    if (contentType.includes('application/json') && text) {
      try {
        const json = JSON.parse(text);
        const message = (json && (json.message || json.error || json.detail)) || response.statusText || `HTTP ${response.status}`;
        const err: any = new Error(message);
        err.status = response.status;
        err.body = json;
        throw err;
      } catch (e) {
        // If parsing fails, use the raw text
      }
    }
    // Use text error body (may be empty)
    const err: any = new Error(text || response.statusText || `HTTP ${response.status}`);
    err.status = response.status;
    err.body = text;
    throw err;
  }

  // Success: parse JSON only when content-type suggests JSON.
  if (contentType.includes('application/json')) {
    if (!text) return {} as T;
    try {
      return JSON.parse(text) as T;
    } catch (e) {
      console.error('[API] Failed to parse JSON response', e, { text });
      throw new Error('Invalid JSON response from server');
    }
  }

  // No JSON body - return an empty object for callers that expect void/empty.
  return {} as T;
}

// Local storage keys for identity (NOT tokens - tokens are in HttpOnly cookies)
const CUSTOMER_ID_KEY = 'claimassist_customer_id';
const FULL_NAME_KEY = 'claimassist_full_name';

// ============================================================================
// Types (reconstructed from backend DTOs and frontend usage)
// ============================================================================

export interface Policy {
  id: number;
  policyNumber: string;
  status: string;
  productType: string;
  planName: string; // coveragePlanName from backend
  coverageAmount: number; // coverageLimitCents / 100 from backend (TODO: backend PolicyResponse doesn't include this)
  premium: number; // annualPremiumCents / 100 from backend (TODO: backend PolicyResponse doesn't include this)
  effectiveDate: string; // ISO date string
  renewalDate: string; // ISO date string
}

export interface Claim {
  id: number;
  claimNumber: string;
  policyId: number;
  incidentType: string;
  status: string;
  estimatedAmountCents?: number;
  approvedAmountCents?: number;
  role?: string;
  incidentDate: string; // ISO date string
  createdAt: string; // ISO date string
}

interface IdentityResponse {
  customerId: number;
  fullName: string;
}

// ============================================================================
// Auth Functions (HttpOnly-cookie based)
// ============================================================================

export function startAuthFlow(): void {
  console.log('AUTHORIZE_URL_BUILT = YES');
  // Initiate OAuth flow by calling the Gateway
  const redirectUri = encodeURIComponent(window.location.origin + '/callback');
  const targetUrl = `${API_BASE}/customer/auth/authorize?redirect_uri=${redirectUri}`;
  console.log('AUTHORIZE_URL =', targetUrl);
  console.log('BROWSER_NAVIGATION_EXECUTED = YES');
  window.location.href = targetUrl;
}

// handleAuthCallback removed: the frontend performs top-level navigation to
// the Gateway callback endpoint (see AuthCallbackPage) so the backend can
// complete the authorization-code exchange and set HttpOnly cookies.

export async function getCurrentCustomer(): Promise<IdentityResponse> {
  console.log('[API] getCurrentCustomer called');
  const response = await fetch(`${API_BASE}/customer/customers/me`, {
    method: 'GET',
    credentials: 'include',
  });

  console.log('[API] getCurrentCustomer response status:', response.status);
  const customer = await parseApiResponse<any>(response);
  console.log('[API] Customer parsed:', { customerId: customer.id, fullName: customer.fullName });

  const identity: IdentityResponse = {
    customerId: customer.id,
    fullName: customer.fullName,
  };

  // Cache identity locally (NOT tokens)
  localStorage.setItem(CUSTOMER_ID_KEY, String(identity.customerId));
  localStorage.setItem(FULL_NAME_KEY, identity.fullName);
  console.log('[API] Identity cached to localStorage');

  return identity;
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE}/customer/auth/logout`, {
    method: 'POST',
    credentials: 'include',
  });

  // Clear local identity cache
  localStorage.removeItem(CUSTOMER_ID_KEY);
  localStorage.removeItem(FULL_NAME_KEY);
}

export async function signUp(username: string, fullName: string, password: string): Promise<void> {
  const response = await fetch(`${API_BASE}/customer/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ username, fullName, password }),
  });

  await parseApiResponse<void>(response);
}

export function getCustomerId(): string | null {
  return localStorage.getItem(CUSTOMER_ID_KEY);
}

export function getFullName(): string | null {
  return localStorage.getItem(FULL_NAME_KEY);
}

// ============================================================================
// Policy Functions
// ============================================================================

export async function getAllPolicies(): Promise<Policy[]> {
  console.log('[API] getAllPolicies called');
  const response = await fetch(`${API_BASE}/policies/all`, {
    method: 'GET',
    credentials: 'include',
  });

  console.log('[API] getAllPolicies response status:', response.status);
  const data = await parseApiResponse<any[]>(response);
  console.log('[API] getAllPolicies parsed, count:', data.length);

  // Transform backend PolicyResponse to frontend Policy type
  // Backend returns: { id, policyNumber, status, coveragePlanName, productType, effectiveDate, renewalDate }
  // Frontend needs: coverageAmount, premium derived from CoveragePlan
  // Since the backend doesn't include coverage details in PolicyResponse, we need to get them separately
  // For now, we'll use placeholder values - this may need backend enhancement
  return data.map((item: any) => ({
    id: item.id,
    policyNumber: item.policyNumber,
    status: item.status,
    productType: item.productType,
    planName: item.coveragePlanName,
    // Backend now provides cents fields; convert to currency units (divide by 100)
    coverageAmount: typeof item.coverageLimitCents === 'number' ? Math.round(item.coverageLimitCents / 100) : 0,
    premium: typeof item.annualPremiumCents === 'number' ? Math.round(item.annualPremiumCents / 100) : 0,
    effectiveDate: item.effectiveDate,
    renewalDate: item.renewalDate,
  }));
}

export async function getPolicy(policyId: string): Promise<Policy> {
  const response = await fetch(`${API_BASE}/policies/${policyId}`, {
    method: 'GET',
    credentials: 'include',
  });

  const data = await parseApiResponse<any>(response);

  // Transform backend PolicyResponse to frontend Policy type
  return {
    id: data.id,
    policyNumber: data.policyNumber,
    status: data.status,
    productType: data.productType,
    planName: data.coveragePlanName,
    coverageAmount: typeof data.coverageLimitCents === 'number' ? Math.round(data.coverageLimitCents / 100) : 0,
    premium: typeof data.annualPremiumCents === 'number' ? Math.round(data.annualPremiumCents / 100) : 0,
    effectiveDate: data.effectiveDate,
    renewalDate: data.renewalDate,
  };
}

// ============================================================================
// Claim Functions
// ============================================================================

export async function getAllClaims(): Promise<Claim[]> {
  const response = await fetch(`${API_BASE}/claims`, {
    method: 'GET',
    credentials: 'include',
  });

  const data = await parseApiResponse<any[]>(response);

  // Transform backend ClaimSummaryResponse to frontend Claim type
  return data.map((item: any) => ({
    id: item.id,
    claimNumber: item.claimNumber,
    policyId: item.policyId,
    incidentType: item.incidentType,
    status: item.status,
    estimatedAmountCents: item.estimatedAmountCents,
    approvedAmountCents: item.approvedAmountCents,
    role: item.role,
    incidentDate: item.incidentDate,
    createdAt: item.createdAt,
  }));
}

export async function getClaim(claimId: string): Promise<Claim> {
  const response = await fetch(`${API_BASE}/claims/${claimId}`, {
    method: 'GET',
    credentials: 'include',
  });

  const data = await parseApiResponse<any>(response);

  // Transform backend ClaimSummaryResponse to frontend Claim type
  return {
    id: data.id,
    claimNumber: data.claimNumber,
    policyId: data.policyId,
    incidentType: data.incidentType,
    status: data.status,
    estimatedAmountCents: data.estimatedAmountCents,
    approvedAmountCents: data.approvedAmountCents,
    role: data.role,
    incidentDate: data.incidentDate,
    createdAt: data.createdAt,
  };
}

export async function createClaim(
  policyId: string,
  incidentType: string,
  incidentDate: string,
  estimatedAmountCents?: number
): Promise<Claim> {
  const response = await fetch(`${API_BASE}/claims`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({
      policyId: Number(policyId),
      incidentType,
      incidentDate: new Date(incidentDate).toISOString(),
      estimatedAmountCents,
    }),
  });

  const data = await parseApiResponse<any>(response);

  // Transform backend ClaimResponse to frontend Claim type
  return {
    id: data.id,
    claimNumber: data.claimNumber,
    policyId: data.policyId,
    incidentType: data.incidentType,
    status: data.status,
    estimatedAmountCents: data.estimatedAmountCents,
    approvedAmountCents: data.approvedAmountCents,
    role: data.role,
    incidentDate: data.incidentDate,
    createdAt: data.createdAt,
  };
}

// ============================================================================
// AI/Agent Functions
// ============================================================================

export async function streamAIMessage(
  message: string,
  claimId: string,
  onChunk: (chunk: string) => void,
  onComplete: () => void,
  onError: (error: string) => void
): Promise<void> {
  try {
    const response = await fetch(`${API_BASE}/agent/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        message,
        claimId: Number(claimId),
      }),
    });

    if (!response.ok) {
      try {
        await parseApiResponse<void>(response);
      } catch (e) {
        onError(e instanceof Error ? e.message : `HTTP ${response.status}`);
      }
      return;
    }

    const reader = response.body?.getReader();
    if (!reader) {
      onError('No response body');
      return;
    }

    const decoder = new TextDecoder();
    let buffer = '';

    const handleSsePayload = (payload: string) => {
      if (!payload || payload === '[DONE]') {
        onComplete();
        return true;
      }

      try {
        const parsed = JSON.parse(payload);
        const eventType = parsed.eventType || 'message';

        if (eventType === 'error') {
          const message = parsed.text || parsed.message || 'AI assistant error';
          onError(message);
          return true;
        }

        if (parsed.done || eventType === 'done') {
          onComplete();
          return true;
        }

        const text = parsed.text ?? parsed.content ?? '';
        if (text) {
          onChunk(text);
        }
      } catch (e) {
        console.error('[API] Failed to parse SSE chunk', e, { payload });
      }

      return false;
    };

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      const events = buffer.split('\n\n');
      buffer = events.pop() || '';

      for (const eventBlock of events) {
        const lines = eventBlock.split('\n');
        let data = '';

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed) continue;
          if (trimmed.startsWith('data:')) {
            data += `${trimmed.slice(5).trim()}\n`;
          }
        }

        const normalized = data.trim();
        if (!normalized) continue;
        if (handleSsePayload(normalized)) {
          return;
        }
      }
    }

    // Flush any trailing SSE payload that arrived without a terminating blank line.
    if (buffer.trim()) {
      const normalized = buffer
        .split('\n')
        .filter((line) => line.trim().startsWith('data:'))
        .map((line) => line.replace(/^data:\s*/, '').trim())
        .join('\n');

      if (normalized) {
        handleSsePayload(normalized);
      }
    }

    onComplete();
  } catch (error) {
    onError(error instanceof Error ? error.message : 'Unknown error');
  }
}

// ============================================================================
// Profile Functions
// ============================================================================

export async function updateCustomer(customerId: string, fullName: string): Promise<void> {
  const response = await fetch(`${API_BASE}/customers/${customerId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ fullName }),
  });

  await parseApiResponse<void>(response);

  // Update local cache
  localStorage.setItem(FULL_NAME_KEY, fullName);
}
