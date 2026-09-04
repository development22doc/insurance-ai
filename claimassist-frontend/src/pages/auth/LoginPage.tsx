import { useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
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
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-blue-50 flex items-center justify-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-6xl">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 lg:gap-12 items-center">

          {/* Left Column - Hero Section */}
          <div className="flex flex-col justify-center lg:pr-8">
            {/* Logo/Branding */}
            <div className="mb-8 sm:mb-12">
              <div className="inline-flex items-center justify-center w-16 h-16 bg-gradient-to-br from-yellow-400 to-yellow-500 rounded-2xl shadow-lg mb-6">
                <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m7 0a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <h1 className="text-4xl sm:text-5xl font-bold text-gray-900 mb-3">ClaimAssist</h1>
              <p className="text-lg sm:text-xl text-gray-600">Secure Insurance Claims Management</p>
            </div>

            {/* Hero Description */}
            <div className="mb-8">
              <p className="text-lg text-gray-700 leading-relaxed mb-4">
                Manage your insurance policies and file claims with confidence. Our secure, transparent platform makes the insurance process simple and fair.
              </p>
              <div className="space-y-3">
                <div className="flex items-start gap-3">
                  <svg className="w-5 h-5 text-yellow-500 flex-shrink-0 mt-1" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                  </svg>
                  <span className="text-gray-700">Fast, transparent claim processing</span>
                </div>
                <div className="flex items-start gap-3">
                  <svg className="w-5 h-5 text-yellow-500 flex-shrink-0 mt-1" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                  </svg>
                  <span className="text-gray-700">AI-assisted document analysis</span>
                </div>
                <div className="flex items-start gap-3">
                  <svg className="w-5 h-5 text-yellow-500 flex-shrink-0 mt-1" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                  </svg>
                  <span className="text-gray-700">Secure, OAuth2-protected access</span>
                </div>
              </div>
            </div>
          </div>

          {/* Right Column - Auth Card */}
          <div className="lg:max-w-md mx-auto w-full">
            <div className="bg-white rounded-2xl border border-gray-200 shadow-xl p-8 sm:p-10">
              {/* Session Expired Alert */}
              {sessionExpired && (
                <div className="mb-6 p-4 bg-amber-50 border border-amber-300 rounded-lg">
                  <div className="flex gap-3">
                    <svg className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4v.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <p className="text-amber-800 text-sm font-medium">
                      Your session has expired. Please sign in again.
                    </p>
                  </div>
                </div>
              )}

              {/* Sign In Section */}
              <div className="mb-8">
                <h2 className="text-2xl font-bold text-gray-900 mb-2">Access Your Account</h2>
                <p className="text-gray-600 text-sm">Sign in securely with OAuth2/OIDC authentication</p>
              </div>

              {/* Primary CTA */}
              <button
                onClick={handleLogin}
                disabled={isLoading}
                className="w-full py-3 px-4 rounded-lg font-semibold transition-all duration-300 flex items-center justify-center gap-2
                  bg-gradient-to-r from-yellow-400 to-yellow-500 hover:from-yellow-500 hover:to-yellow-600
                  text-gray-900 shadow-md hover:shadow-lg disabled:opacity-60 disabled:cursor-not-allowed
                  active:scale-95 transform"
              >
                {isLoading ? (
                  <>
                    <span className="inline-block w-4 h-4 border-2 border-gray-900 border-t-transparent rounded-full animate-spin"></span>
                    <span>Signing in...</span>
                  </>
                ) : (
                  <>
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1" />
                    </svg>
                    <span>Sign In with SSO</span>
                  </>
                )}
              </button>

              {/* Divider */}
              <div className="relative my-8">
                <div className="absolute inset-0 flex items-center">
                  <div className="w-full border-t border-gray-300"></div>
                </div>
                <div className="relative flex justify-center text-sm">
                  <span className="px-2 bg-white text-gray-500 font-medium">New to ClaimAssist?</span>
                </div>
              </div>

              {/* Secondary CTA */}
              <a
                href="/register"
                className="block w-full text-center py-3 px-4 rounded-lg font-semibold text-gray-900 border-2 border-gray-300 hover:border-gray-400 hover:bg-gray-50 transition-all duration-300 active:scale-95 transform"
              >
                Create New Account
              </a>

              {/* Security Info */}
              <div className="mt-8 pt-6 border-t border-gray-200 space-y-3">
                <div className="flex gap-2 text-sm text-gray-600">
                  <svg className="w-5 h-5 text-green-600 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M5.293 9.707a1 1 0 010-1.414L8.586 5a1 1 0 111.414 1.414L7.414 8l2.586 2.586a1 1 0 11-1.414 1.414L6 9.414 3.414 12a1 1 0 11-1.414-1.414L5.293 9.707z" clipRule="evenodd" />
                  </svg>
                  <p className="leading-relaxed">
                    <strong className="text-gray-900">OAuth2/OIDC with PKCE</strong> — Industry-standard, passwordless authentication
                  </p>
                </div>
                <div className="flex gap-2 text-sm text-gray-600">
                  <svg className="w-5 h-5 text-green-600 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M5.293 9.707a1 1 0 010-1.414L8.586 5a1 1 0 111.414 1.414L7.414 8l2.586 2.586a1 1 0 11-1.414 1.414L6 9.414 3.414 12a1 1 0 11-1.414-1.414L5.293 9.707z" clipRule="evenodd" />
                  </svg>
                  <p className="leading-relaxed">
                    <strong className="text-gray-900">No passwords stored</strong> — Your credentials are secure with our identity provider
                  </p>
                </div>
              </div>

              {/* Footer Links */}
              <div className="mt-8 flex justify-center items-center gap-2 text-xs">
                <a href="/about" className="text-gray-600 hover:text-gray-900 transition-colors font-medium">
                  About
                </a>
                <span className="text-gray-300">•</span>
                <a href="/contact" className="text-gray-600 hover:text-gray-900 transition-colors font-medium">
                  Contact
                </a>
                <span className="text-gray-300">•</span>
                <a href="/products" className="text-gray-600 hover:text-gray-900 transition-colors font-medium">
                  Products
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
