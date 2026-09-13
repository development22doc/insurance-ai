import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import * as api from '@/lib/api';
import { navigate } from '@/lib/router';

type AuthContextType = {
  isAuthenticated: boolean;
  customerId: string | null;
  fullName: string | null;
  loading: boolean;
  signIn: () => void;
  signUp: (username: string, fullName: string, password: string) => Promise<{ error: string | null }>;
  signOut: () => Promise<void>;
  refreshProfile: () => Promise<void>;
};

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [customerId, setCustomerId] = useState<string | null>(null);
  const [fullName, setFullName] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Try to fetch current customer profile from backend
    // This will work if we have valid HttpOnly cookies from the OAuth callback
    const fetchIdentity = async () => {
      try {
        console.log('[AuthContext] Attempting to fetch current customer');
        const identity = await api.getCurrentCustomer();
        console.log('[AuthContext] Identity fetched successfully:', { customerId: identity.customerId, fullName: identity.fullName });
        setIsAuthenticated(true);
        setCustomerId(String(identity.customerId));
        setFullName(identity.fullName);
      } catch (error) {
        console.log('[AuthContext] No valid session or fetch failed, user not authenticated');
        // Check for cached identity as fallback
        const cid = api.getCustomerId();
        const name = api.getFullName();
        if (cid && name) {
          console.log('[AuthContext] Using cached identity as fallback');
          setIsAuthenticated(true);
          setCustomerId(cid);
          setFullName(name);
        } else {
          setIsAuthenticated(false);
          setCustomerId(null);
          setFullName(null);
        }
      } finally {
        setLoading(false);
      }
    };

    fetchIdentity();
  }, []);

  const signOut = async () => {
    await api.logout();
    setIsAuthenticated(false);
    setCustomerId(null);
    setFullName(null);
    navigate('/login');
  };

  const signIn = () => {
    console.log('START_AUTH_FLOW_CALLED = YES');
    api.startAuthFlow();
  };

  const signUp = async (username: string, fullName: string, password: string) => {
    try {
      await api.signUp(username, fullName, password);
      // Do NOT auto-authenticate - user must sign in
      return { error: null };
    } catch (error) {
      return { error: error instanceof Error ? error.message : 'Sign up failed' };
    }
  };

  const refreshProfile = async () => {
    try {
      const identity = await api.getCurrentCustomer();
      setFullName(identity.fullName);
    } catch (error) {
      console.error('[AuthContext] Failed to refresh profile', error);
    }
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, customerId, fullName, loading, signIn, signUp, signOut, refreshProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
