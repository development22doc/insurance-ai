// Token storage utilities
//
// Security Considerations:
// - Access tokens are stored in sessionStorage (cleared on tab close)
// - Refresh tokens are stored in a more secure approach
// - We use httpOnly cookies when possible, but for this architecture
//   we need to work with the existing backend contract
//
// Trade-off: The existing backend returns tokens directly to the browser.
// We handle this securely by:
// 1. Using sessionStorage for access tokens (not localStorage)
// 2. Not storing refresh tokens in localStorage
// 3. Clearing tokens on logout and session expiration
// 4. Not logging tokens anywhere

export interface TokenData {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  refreshExpiresIn: number;
  expiresAt: number; // Calculated expiration timestamp
  refreshExpiresAt: number; // Calculated refresh expiration timestamp
}

const ACCESS_TOKEN_KEY = 'access_token';
const REFRESH_TOKEN_KEY = 'refresh_token';
const TOKEN_EXPIRY_KEY = 'token_expiry';
const REFRESH_EXPIRY_KEY = 'refresh_expiry';

/**
 * Store access token in sessionStorage (cleared on tab close)
 */
export function setAccessToken(token: string): void {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
}

/**
 * Get access token from sessionStorage
 */
export function getAccessToken(): string | null {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY);
}

/**
 * Remove access token from sessionStorage
 */
export function clearAccessToken(): void {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
}

/**
 * Store refresh token securely
 * For security, we use sessionStorage rather than localStorage
 * This means the user must re-authenticate if they close the tab
 */
export function setRefreshToken(token: string, expiresIn: number): void {
  sessionStorage.setItem(REFRESH_TOKEN_KEY, token);
  const expiresAt = Date.now() + expiresIn * 1000;
  sessionStorage.setItem(REFRESH_EXPIRY_KEY, expiresAt.toString());
}

/**
 * Get refresh token from storage
 */
export function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY);
}

/**
 * Check if refresh token is expired
 */
export function isRefreshTokenExpired(): boolean {
  const expiresAt = sessionStorage.getItem(REFRESH_EXPIRY_KEY);
  if (!expiresAt) return true;
  return Date.now() > parseInt(expiresAt, 10);
}

/**
 * Remove refresh token from storage
 */
export function clearRefreshToken(): void {
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_EXPIRY_KEY);
}

/**
 * Store complete token data
 */
export function setTokenData(data: TokenData): void {
  setAccessToken(data.accessToken);
  setRefreshToken(data.refreshToken, data.refreshExpiresIn);
  sessionStorage.setItem(TOKEN_EXPIRY_KEY, data.expiresAt.toString());
}

/**
 * Check if access token is expired (with 5 minute buffer)
 */
export function isAccessTokenExpired(): boolean {
  const expiresAt = sessionStorage.getItem(TOKEN_EXPIRY_KEY);
  if (!expiresAt) return true;
  // Add 5 minute buffer (300000ms) to refresh before actual expiration
  return Date.now() > (parseInt(expiresAt, 10) - 300000);
}

/**
 * Clear all authentication data
 */
export function clearAllTokens(): void {
  clearAccessToken();
  clearRefreshToken();
  sessionStorage.removeItem(TOKEN_EXPIRY_KEY);
}

/**
 * Check if user is authenticated (has valid access token)
 */
export function isAuthenticated(): boolean {
  const token = getAccessToken();
  if (!token) return false;
  return !isAccessTokenExpired();
}
