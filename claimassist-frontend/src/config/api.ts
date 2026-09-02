// API Configuration
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
// Note: defaulting to an empty string makes fetch() use the current origin so
// the browser calls the configured API Gateway (same-origin) rather than a
// hardcoded localhost address. Set VITE_API_BASE_URL in dev if a different
// gateway base URL is required.

export const API_CONFIG = {
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
};

// Endpoint paths
export const API_ENDPOINTS = {
  // Auth
  AUTH_SIGNUP: '/api/v1/auth/signup',
  AUTH_AUTHORIZE: '/api/v1/auth/authorize',
  AUTH_CALLBACK: '/api/v1/auth/callback',
  AUTH_REFRESH: '/api/v1/auth/refresh',
  AUTH_LOGOUT: '/api/v1/auth/logout',

  // Customer
  CUSTOMER_UPDATE: (customerId: number) => `/api/v1/customers/${customerId}`,
  CUSTOMER_DELETE: (customerId: number) => `/api/v1/customers/${customerId}`,

  // Policies
  POLICIES_ALL: '/api/v1/policies/all',
  POLICY_BY_ID: (policyId: number) => `/api/v1/policies/${policyId}`,
  POLICY_CREATE: '/api/v1/policies',
  POLICY_UPDATE: (policyId: number) => `/api/v1/policies/${policyId}`,
  POLICY_DELETE: (policyId: number) => `/api/v1/policies/${policyId}`,
  POLICY_COVERAGE: (policyId: number) => `/internal/v1/policies/${policyId}/coverage`,

  // Claims
  CLAIMS: '/api/v1/claims',
  CLAIM_BY_ID: (id: number) => `/api/v1/claims/${id}`,
  CLAIM_UPDATE_STATUS: (id: number) => `/api/v1/claims/${id}/status`,
  CLAIM_STATUS: (claimId: number) => `/internal/v1/claims/${claimId}/status`,
  CLAIM_DOCUMENTS: (claimId: number) => `/internal/v1/claims/${claimId}/documents`,
  CLAIM_PERMISSIONS_CHECK: (claimId: number) => `/internal/v1/claims/${claimId}/permissions/check`,

  // Agent/AI
  AGENT_STREAM: '/api/v1/agent/stream',
  AGENT_CLAIM_HISTORY: (claimId: number) => `/api/v1/agent/claims/${claimId}`,
} as const;
