import { useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { Button } from '../../components/ui';
// PKCE is generated server-side by customer-service; frontend no longer generates local PKCE state.

export function LoginPage() {
  const { login, isAuthenticated, isLoading } = useAuth();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // Check for session expired parameter
  const sessionExpired = searchParams.get('session') === 'expired';

  // Redirect if already authenticated
  useEffect(() => {
    if (isAuthenticated && !isLoading) {
      navigate('/dashboard');
    }
  }, [isAuthenticated, isLoading, navigate]);

  // Initiate OAuth login flow
  const handleLogin = async () => {
    try {
      // Initiate auth flow via backend authorize endpoint. The server generates
      // and stores PKCE state and verifier; frontend must not generate its own.
      await login();
    } catch (error) {
      console.error('Login failed:', error);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 px-4">
      <div className="max-w-md w-full">
        <div className="bg-white rounded-2xl shadow-xl p-8">
          {/* Logo/Branding */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 bg-blue-600 rounded-full mb-4">
              <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
            </div>
            <h1 className="text-3xl font-bold text-gray-900">ClaimAssist</h1>
            <p className="text-gray-600 mt-2">Secure Insurance Claims Management</p>
          </div>

          {/* Session Expired Message */}
          {sessionExpired && (
            <div className="mb-6 p-4 bg-amber-50 border border-amber-200 rounded-lg">
              <p className="text-amber-800 text-sm">
                Your session has expired. Please sign in again.
              </p>
            </div>
          )}

          {/* Login Button */}
          <div className="space-y-4">
            <Button
              onClick={handleLogin}
              disabled={isLoading}
              className="w-full py-3 text-lg"
              size="lg"
            >
              {isLoading ? 'Signing in...' : 'Sign In with Keycloak'}
            </Button>

            <div className="text-center">
              <p className="text-gray-600">
                Don't have an account?{' '}
                <a href="/register" className="text-blue-600 hover:text-blue-700 font-medium">
                  Register
                </a>
              </p>
            </div>
          </div>

          {/* Security Note */}
          <div className="mt-8 pt-6 border-t border-gray-200">
            <p className="text-xs text-gray-500 text-center">
              Your sign-in is secured by OAuth2/OIDC with PKCE
            </p>
          </div>
        </div>

        {/* Footer Links */}
        <div className="mt-6 text-center space-x-4">
          <a href="/about" className="text-sm text-gray-600 hover:text-gray-900">
            About
          </a>
          <span className="text-gray-300">|</span>
          <a href="/contact" className="text-sm text-gray-600 hover:text-gray-900">
            Contact
          </a>
          <span className="text-gray-300">|</span>
          <a href="/products" className="text-sm text-gray-600 hover:text-gray-900">
            Products
          </a>
        </div>
      </div>
    </div>
  );
}
