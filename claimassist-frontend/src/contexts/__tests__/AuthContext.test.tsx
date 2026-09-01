import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { AuthProvider, useAuth } from '../AuthContext';
import { apiClient } from '../../services/api-client-impl';
import { clearAllTokens } from '../../lib/token-storage';

// Mock API client
vi.mock('../../services/api-client-impl', () => ({
  apiClient: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

// Mock token storage
vi.mock('../../lib/token-storage', () => ({
  setTokenData: vi.fn(),
  getAccessToken: vi.fn(),
  clearAllTokens: vi.fn(),
  isAuthenticated: vi.fn(() => false),
  isAccessTokenExpired: vi.fn(() => true),
}));

// Mock window.location
const mockLocation = { href: '' };
Object.defineProperty(window, 'location', {
  value: mockLocation,
  writable: true,
});

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    mockLocation.href = '';
  });

  describe('initial state', () => {
    it('should initialize with unauthenticated state when no token exists', () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.isLoading).toBe(false);
      expect(result.current.user).toBeNull();
    });
  });

  describe('signup', () => {
    it('should call signup API with correct credentials', async () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      vi.mocked(apiClient.post).mockResolvedValue({});

      await act(async () => {
        await result.current.signup('test@example.com', 'Test User', 'password123');
      });

      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/auth/signup', {
        username: 'test@example.com',
        fullName: 'Test User',
        password: 'password123',
      });
    });

    it('should handle signup errors', async () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      vi.mocked(apiClient.post).mockRejectedValue(new Error('Email already exists'));

      await act(async () => {
        try {
          await result.current.signup('test@example.com', 'Test User', 'password123');
        } catch (error) {
          expect(error).toBeInstanceOf(Error);
        }
      });

      expect(result.current.error).toBe('Email already exists');
    });
  });

  describe('logout', () => {
    it('should clear tokens and redirect to login', async () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      vi.mocked(apiClient.post).mockResolvedValue({});

      await act(async () => {
        await result.current.logout();
      });

      expect(clearAllTokens).toHaveBeenCalled();
      expect(mockLocation.href).toBe('/login');
    });

    it('should handle logout errors gracefully', async () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      vi.mocked(apiClient.post).mockRejectedValue(new Error('Network error'));

      await act(async () => {
        await result.current.logout();
      });

      // Should still clear local state even if backend logout fails
      expect(clearAllTokens).toHaveBeenCalled();
      expect(mockLocation.href).toBe('/login');
    });
  });

  describe('role checking', () => {
    it('should correctly check if user has specific role', () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      // Mock user with CUSTOMER role
      act(() => {
        sessionStorage.setItem('auth_user', JSON.stringify({
          customerId: 1,
          fullName: 'Test User',
          roles: ['CUSTOMER'],
          userId: '123',
        }));
      });

      expect(result.current.hasRole('CUSTOMER')).toBe(true);
      expect(result.current.hasRole('ADJUSTER')).toBe(false);
    });

    it('should correctly check if user has any of specified roles', () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      act(() => {
        sessionStorage.setItem('auth_user', JSON.stringify({
          customerId: 1,
          fullName: 'Test User',
          roles: ['CUSTOMER'],
          userId: '123',
        }));
      });

      expect(result.current.hasAnyRole(['CUSTOMER', 'ADJUSTER'])).toBe(true);
      expect(result.current.hasAnyRole(['ADJUSTER', 'AUDITOR'])).toBe(false);
    });
  });
});
