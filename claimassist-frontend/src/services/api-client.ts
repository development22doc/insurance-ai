import { API_CONFIG, API_ENDPOINTS } from '../config/api';
import type { ApiError } from '../types';
import { getAccessToken, clearAllTokens, getRefreshToken } from '../lib/token-storage';

// Track in-flight refresh requests to avoid multiple simultaneous refreshes
let refreshPromise: Promise<void> | null = null;

class ApiClient {
  private baseURL: string;
  private timeout: number;
  private defaultHeaders: Record<string, string>;

  constructor() {
    this.baseURL = API_CONFIG.baseURL;
    this.timeout = API_CONFIG.timeout;
    this.defaultHeaders = API_CONFIG.headers;
  }

  private getAuthHeader(): Record<string, string> {
    const token = getAccessToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  // Handle 401 errors by attempting token refresh
  private async handle401Error(): Promise<void> {
    // If already refreshing, wait for that to complete
    if (refreshPromise) {
      await refreshPromise;
      return;
    }

    // Start refresh process
    refreshPromise = this.performTokenRefresh();

    try {
      await refreshPromise;
    } finally {
      refreshPromise = null;
    }
  }

  // Perform token refresh by calling backend refresh endpoint
  private async performTokenRefresh(): Promise<void> {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      // No refresh token - clear auth and redirect to login
      clearAllTokens();
      window.location.href = '/login?session=expired';
      throw new Error('No refresh token available');
    }

    try {
      const response = await fetch(
        `${API_CONFIG.baseURL}${API_ENDPOINTS.AUTH_REFRESH}?refreshToken=${encodeURIComponent(refreshToken)}`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
        }
      );

      if (!response.ok) {
        throw new Error('Token refresh failed');
      }

      const data = await response.json();

      // Store new tokens
      sessionStorage.setItem('access_token', data.accessToken);
      sessionStorage.setItem('refresh_token', data.refreshToken);
      sessionStorage.setItem('token_expiry', (Date.now() + data.expiresIn * 1000).toString());
      sessionStorage.setItem('refresh_expiry', (Date.now() + data.refreshExpiresIn * 1000).toString());

      // Update user data if provided
      if (data.customerId && data.fullName) {
        const userData = JSON.parse(sessionStorage.getItem('auth_user') || '{}');
        userData.customerId = data.customerId;
        userData.fullName = data.fullName;
        sessionStorage.setItem('auth_user', JSON.stringify(userData));
      }
    } catch (error) {
      // Refresh failed - clear auth and redirect to login
      clearAllTokens();
      window.location.href = '/login?session=expired';
      throw error;
    }
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${this.baseURL}${endpoint}`;
    const headers = {
      ...this.defaultHeaders,
      ...this.getAuthHeader(),
      ...options.headers,
    };

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeout);

    try {
      let response = await fetch(url, {
        ...options,
        headers,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      // Handle 401 - attempt token refresh
      if (response.status === 401 && getAccessToken()) {
        await this.handle401Error();

        // Retry request with new token
        response = await fetch(url, {
          ...options,
          headers: {
            ...headers,
            ...this.getAuthHeader(),
          },
          signal: controller.signal,
        });
      }

      if (!response.ok) {
        const error: ApiError = await response.json().catch(() => ({
          status: response.status,
          message: response.statusText || 'An error occurred',
          timestamp: new Date().toISOString(),
          correlationId: '',
        }));
        throw new Error(error.message || 'Request failed');
      }

      return await response.json();
    } catch (error) {
      clearTimeout(timeoutId);
      throw error;
    }
  }

  async get<T>(endpoint: string, options?: RequestInit): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: 'GET' });
  }

  async post<T>(endpoint: string, data?: unknown, options?: RequestInit): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: 'POST',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  async put<T>(endpoint: string, data?: unknown, options?: RequestInit): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: 'PUT',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  async patch<T>(endpoint: string, data?: unknown, options?: RequestInit): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: 'PATCH',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  async delete<T>(endpoint: string, options?: RequestInit): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: 'DELETE' });
  }

  // SSE streaming support for AI agent
  async stream(endpoint: string, data?: unknown): Promise<ReadableStream<Uint8Array>> {
    const url = `${this.baseURL}${endpoint}`;
    const headers = {
      ...this.defaultHeaders,
      ...this.getAuthHeader(),
      Accept: 'text/event-stream',
    };

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: data ? JSON.stringify(data) : undefined,
    });

    if (!response.ok) {
      throw new Error(`Stream request failed: ${response.statusText}`);
    }

    return response.body!;
  }
}

export const apiClient = new ApiClient();
export default apiClient;
