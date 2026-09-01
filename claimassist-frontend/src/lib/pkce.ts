// PKCE (Proof Key for Code Exchange) utilities for OAuth2/OIDC
// This is required for the public client flow (no client secret)

export interface PKCEState {
  codeVerifier: string;
  codeChallenge: string;
  state: string;
}

/**
 * Generate a random string for PKCE code verifier
 * Uses crypto.getRandomValues for secure random generation
 */
function generateRandomString(length: number): string {
  const charset = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  const values = new Uint8Array(length);
  crypto.getRandomValues(values);
  let result = '';
  for (let i = 0; i < length; i++) {
    result += charset[values[i] % charset.length];
  }
  return result;
}

/**
 * Generate code verifier (43-128 characters)
 */
export function generateCodeVerifier(): string {
  return generateRandomString(128);
}

/**
 * Generate code challenge from code verifier using SHA-256
 * This is sent to Keycloak during authorization
 */
export async function generateCodeChallenge(codeVerifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(codeVerifier);
  const hash = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hash));
  const hashBase64 = btoa(String.fromCharCode(...hashArray));
  // Convert base64 to base64url (replace + with -, / with _, remove =)
  return hashBase64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Generate a random state parameter for CSRF protection
 */
export function generateState(): string {
  return generateRandomString(32);
}

/**
 * Generate complete PKCE state for OAuth flow
 */
export async function generatePKCEState(): Promise<PKCEState> {
  const codeVerifier = generateCodeVerifier();
  const codeChallenge = await generateCodeChallenge(codeVerifier);
  const state = generateState();

  return {
    codeVerifier,
    codeChallenge,
    state,
  };
}

/**
 * Store PKCE state temporarily (in sessionStorage for the OAuth flow)
 * Only valid for the current tab/session
 */
export function storePKCEState(pkceState: PKCEState): void {
  sessionStorage.setItem('pkce_state', JSON.stringify(pkceState));
}

/**
 * Retrieve and remove PKCE state
 * Should be called during callback processing
 */
export function consumePKCEState(): PKCEState | null {
  const stored = sessionStorage.getItem('pkce_state');
  if (!stored) return null;

  sessionStorage.removeItem('pkce_state');
  try {
    return JSON.parse(stored);
  } catch {
    return null;
  }
}
