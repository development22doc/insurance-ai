import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { AuthProvider, useAuth } from '../AuthContext';
import * as api from '@/lib/api';

// Mock API module
vi.mock('@/lib/api', () => ({
  signUp: vi.fn(),
  startAuthFlow: vi.fn(),
  logout: vi.fn(),
  getCurrentCustomer: vi.fn(),
  getCustomerId: vi.fn(),
  getFullName: vi.fn(),
}));

// Mock router
vi.mock('@/lib/router', () => ({
  navigate: vi.fn(),
}));

describe('AuthContext - Auth Flows', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    window.location.href = '';
    window.location.hash = '';
  });

  describe('Sign In Flow', () => {
    it('should start OAuth flow when signIn is called', () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      act(() => {
        result.current.signIn();
      });

      expect(api.startAuthFlow).toHaveBeenCalled();
    });

    it('should redirect to Keycloak via backend authorize endpoint', () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      act(() => {
        result.current.signIn();
      });

      expect(api.startAuthFlow).toHaveBeenCalledWith();
    });
  });

  describe('OAuth Callback Flow', () => {
    it('should restore session from localStorage on mount', async () => {
      vi.mocked(api.getCurrentCustomer).mockRejectedValue(new Error('Network error'));
      localStorage.setItem('claimassist_customer_id', '123');
      localStorage.setItem('claimassist_full_name', 'Test User');
      vi.mocked(api.getCustomerId).mockReturnValue('123');
      vi.mocked(api.getFullName).mockReturnValue('Test User');

      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      // Wait for the initial fetch to complete
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0));
      });

      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.customerId).toBe('123');
      expect(result.current.fullName).toBe('Test User');
    });

    it('should handle missing identity during session restoration', async () => {
      vi.mocked(api.getCurrentCustomer).mockRejectedValue(new Error('Network error'));
      vi.mocked(api.getCustomerId).mockReturnValue(null);
      vi.mocked(api.getFullName).mockReturnValue(null);

      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      // Wait for the initial fetch to complete
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0));
      });

      expect(result.current.isAuthenticated).toBe(false);
    });
  });

  describe('Session Restoration', () => {
    it('should try to fetch identity from backend on mount', async () => {
      const mockIdentity = { customerId: 123, fullName: 'Test User' };
      vi.mocked(api.getCurrentCustomer).mockResolvedValue(mockIdentity);

      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      // Wait for the initial fetch to complete
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0));
      });

      expect(api.getCurrentCustomer).toHaveBeenCalled();
    });

    it('should fallback to localStorage if backend fetch fails', async () => {
      vi.mocked(api.getCurrentCustomer).mockRejectedValue(new Error('Network error'));
      localStorage.setItem('claimassist_customer_id', '123');
      localStorage.setItem('claimassist_full_name', 'Test User');
      vi.mocked(api.getCustomerId).mockReturnValue('123');
      vi.mocked(api.getFullName).mockReturnValue('Test User');

      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      // Wait for the initial fetch to complete
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0));
      });

      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.customerId).toBe('123');
      expect(result.current.fullName).toBe('Test User');
    });
  });

  describe('Logout Flow', () => {
    it('should clear auth state on logout', async () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      // Set authenticated state
      localStorage.setItem('claimassist_customer_id', '123');
      localStorage.setItem('claimassist_full_name', 'Test User');
      vi.mocked(api.getCustomerId).mockReturnValue('123');
      vi.mocked(api.getFullName).mockReturnValue('Test User');

      await act(async () => {
        await result.current.signOut();
      });

      expect(api.logout).toHaveBeenCalled();
      expect(result.current.isAuthenticated).toBe(false);
    });

    it('should redirect to login after logout', async () => {
      const { navigate } = await import('@/lib/router');

      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      await act(async () => {
        await result.current.signOut();
      });

      expect(navigate).toHaveBeenCalledWith('/login');
    });
  });

  describe('Back to Sign In', () => {
    it('should allow user to return to sign in page', () => {
      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      act(() => {
        result.current.signIn();
      });

      expect(api.startAuthFlow).toHaveBeenCalled();
    });
  });

  describe('Signup Flow', () => {
    it('should send correct signup payload', async () => {
      vi.mocked(api.signUp).mockResolvedValue(undefined);

      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      await act(async () => {
        const response = await result.current.signUp('test@example.com', 'Test User', 'password123');
        expect(response.error).toBeNull();
      });

      expect(api.signUp).toHaveBeenCalledWith('test@example.com', 'Test User', 'password123');
    });

    it('should not automatically authenticate after signup', async () => {
      vi.mocked(api.signUp).mockResolvedValue(undefined);
      vi.mocked(api.getCustomerId).mockReturnValue(null);
      vi.mocked(api.getFullName).mockReturnValue(null);

      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      await act(async () => {
        await result.current.signUp('test@example.com', 'Test User', 'password123');
      });

      // Should not be authenticated after signup (user must sign in)
      expect(result.current.isAuthenticated).toBe(false);
    });

    it('should handle signup errors', async () => {
      vi.mocked(api.signUp).mockRejectedValue(new Error('Email already exists'));

      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      await act(async () => {
        const response = await result.current.signUp('test@example.com', 'Test User', 'password123');
        expect(response.error).toBe('Email already exists');
      });
    });
  });

  describe('Profile Updates', () => {
    it('should refresh profile from backend', async () => {
      const mockIdentity = { customerId: 123, fullName: 'Updated Name' };
      vi.mocked(api.getCurrentCustomer).mockResolvedValue(mockIdentity);

      const { result } = renderHook(() => useAuth(), {
        wrapper: AuthProvider,
      });

      await act(async () => {
        await result.current.refreshProfile();
      });

      expect(result.current.fullName).toBe('Updated Name');
    });
  });
});
