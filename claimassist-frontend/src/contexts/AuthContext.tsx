import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { AuthResponse, RealmRole } from '../types';
import {
  setTokenData,
  getAccessToken,
  clearAllTokens,
  isAccessTokenExpired
} from '../lib/token-storage';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';

// Decode JWT to extract roles
function decodeJWT(token: string): any {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
}

// Extract roles from JWT
function extractRoles(token: string): RealmRole[] {
  const decoded = decodeJWT(token);
  if (!decoded || !decoded.realm_access || !decoded.realm_access.roles) {
    return [];
  }
  return decoded.realm_access.roles.filter((role: string): role is RealmRole =>
    ['CUSTOMER', 'ADJUSTER', 'AUDITOR', 'ADMIN', 'SUPPORT'].includes(role)
  );
}

// Extract user ID from JWT (userId claim)
function extractUserId(token: string): string | null {
  const decoded = decodeJWT(token);
  return decoded?.userId || null;
}

export interface AuthUser {
  customerId: number;
  fullName: string;
  roles: RealmRole[];
  userId: string | null;
}

export interface AuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: AuthUser | null;
  error: string | null;
}

export interface AuthContextType extends AuthState {
  login: () => Promise<void>;
  signup: (username: string, fullName: string, password: string) => Promise<void>;
  handleCallback: (code: string, state: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshAccessToken: () => Promise<void>;
  hasRole: (role: RealmRole) => boolean;
  hasAnyRole: (roles: RealmRole[]) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    isLoading: true,
    user: null,
    error: null,
  });

  // Initialize auth state on mount
  useEffect(() => {
    const initAuth = () => {
      const token = getAccessToken();
      if (token && !isAccessTokenExpired()) {
        const roles = extractRoles(token);
        const userId = extractUserId(token);

        // Load user data from storage (set during callback)
        const storedUser = sessionStorage.getItem('auth_user');
        const user = storedUser ? JSON.parse(storedUser) : null;

        setState({
          isAuthenticated: true,
          isLoading: false,
          user: user ? { ...user, roles, userId } : null,
          error: null,
        });
      } else {
        // Clear any stale tokens
        clearAllTokens();
        setState({
          isAuthenticated: false,
          isLoading: false,
          user: null,
          error: null,
        });
      }
    };

    initAuth();
  }, []);

  // Login - initiate OAuth flow
  const login = useCallback(async () => {
    try {
      setState(prev => ({ ...prev, isLoading: true, error: null }));

      // Call backend authorize endpoint
      // This will redirect to Keycloak
      window.location.href = `${import.meta.env.VITE_API_BASE_URL}${API_ENDPOINTS.AUTH_AUTHORIZE}`;
    } catch (error) {
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error instanceof Error ? error.message : 'Login failed',
      }));
    }
  }, []);

  // Signup - register new user
  const signup = useCallback(async (username: string, fullName: string, password: string) => {
    try {
      setState(prev => ({ ...prev, isLoading: true, error: null }));

      await apiClient.post(API_ENDPOINTS.AUTH_SIGNUP, {
        username,
        fullName,
        password,
      });

      // After successful signup, redirect to login
      // Backend does not auto-login after signup
      setState(prev => ({ ...prev, isLoading: false }));
      window.location.href = '/login';
    } catch (error) {
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error instanceof Error ? error.message : 'Registration failed',
      }));
      throw error;
    }
  }, []);

  // Handle OAuth callback
  const handleCallback = useCallback(async (code: string, state: string) => {
    try {
      setState(prev => ({ ...prev, isLoading: true, error: null }));

      const response = await apiClient.get<AuthResponse>(
        `${API_ENDPOINTS.AUTH_CALLBACK}?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`
      );

      // Calculate expiration timestamps
      const expiresAt = Date.now() + (response.expiresIn * 1000);
      const refreshExpiresAt = Date.now() + (response.refreshExpiresIn * 1000);

      // Store tokens
      setTokenData({
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        tokenType: response.tokenType,
        expiresIn: response.expiresIn,
        refreshExpiresIn: response.refreshExpiresIn,
        expiresAt,
        refreshExpiresAt,
      });

      // Extract roles from access token
      const roles = extractRoles(response.accessToken);
      const userId = extractUserId(response.accessToken);

      // Store user data
      const userData: AuthUser = {
        customerId: response.customerId,
        fullName: response.fullName,
        roles,
        userId,
      };
      sessionStorage.setItem('auth_user', JSON.stringify(userData));

      setState({
        isAuthenticated: true,
        isLoading: false,
        user: userData,
        error: null,
      });

      // Redirect to appropriate dashboard based on role
      if (roles.includes('CUSTOMER')) {
        window.location.href = '/dashboard';
      } else if (roles.includes('ADJUSTER')) {
        window.location.href = '/operations';
      } else if (roles.includes('AUDITOR')) {
        window.location.href = '/operations';
      } else {
        window.location.href = '/dashboard';
      }
    } catch (error) {
      clearAllTokens();
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error instanceof Error ? error.message : 'Authentication failed',
      }));
      throw error;
    }
  }, []);

  // Refresh access token
  const refreshAccessToken = useCallback(async () => {
    const refreshToken = sessionStorage.getItem('refresh_token');
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }

    try {
      const response = await apiClient.post<AuthResponse>(
        `${API_ENDPOINTS.AUTH_REFRESH}?refreshToken=${encodeURIComponent(refreshToken)}`
      );

      const expiresAt = Date.now() + (response.expiresIn * 1000);
      const refreshExpiresAt = Date.now() + (response.refreshExpiresIn * 1000);

      setTokenData({
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        tokenType: response.tokenType,
        expiresIn: response.expiresIn,
        refreshExpiresIn: response.refreshExpiresIn,
        expiresAt,
        refreshExpiresAt,
      });

      const roles = extractRoles(response.accessToken);
      const userId = extractUserId(response.accessToken);

      const userData: AuthUser = {
        customerId: response.customerId,
        fullName: response.fullName,
        roles,
        userId,
      };
      sessionStorage.setItem('auth_user', JSON.stringify(userData));

      setState(prev => ({
        ...prev,
        user: userData,
      }));
    } catch (error) {
      // Refresh failed - clear auth state
      clearAllTokens();
      setState({
        isAuthenticated: false,
        isLoading: false,
        user: null,
        error: 'Session expired. Please sign in again.',
      });
      throw error;
    }
  }, []);

  // Logout
  const logout = useCallback(async () => {
    const refreshToken = sessionStorage.getItem('refresh_token');

    try {
      if (refreshToken) {
        await apiClient.post(
          `${API_ENDPOINTS.AUTH_LOGOUT}?refreshToken=${encodeURIComponent(refreshToken)}`
        );
      }
    } catch (error) {
      // Log error but continue with local logout
      console.error('Backend logout failed:', error);
    } finally {
      // Always clear local auth state
      clearAllTokens();
      sessionStorage.removeItem('auth_user');
      setState({
        isAuthenticated: false,
        isLoading: false,
        user: null,
        error: null,
      });
      window.location.href = '/login';
    }
  }, []);

  // Role checking helpers
  // Read roles from sessionStorage to handle cases where state isn't refreshed synchronously (tests and multi-tab)
  const getStoredRoles = useCallback((): RealmRole[] => {
    const stored = sessionStorage.getItem('auth_user');
    if (!stored) return [];
    try {
      const parsed = JSON.parse(stored);
      return parsed?.roles ?? [];
    } catch {
      return [];
    }
  }, []);

  const hasRole = useCallback((role: RealmRole): boolean => {
    const roles = getStoredRoles();
    return roles.includes(role);
  }, [getStoredRoles]);

  const hasAnyRole = useCallback((roles: RealmRole[]): boolean => {
    const stored = getStoredRoles();
    if (stored.length === 0) return false;
    return roles.some(r => stored.includes(r));
  }, [getStoredRoles]);

  const value: AuthContextType = {
    ...state,
    login,
    signup,
    handleCallback,
    logout,
    refreshAccessToken,
    hasRole,
    hasAnyRole,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
