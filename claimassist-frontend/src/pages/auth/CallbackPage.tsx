import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
// Rely on server-side PKCE state stored in Redis; frontend no longer consumes local PKCE state.
import { LoadingState, ErrorState } from '../../components/ui';

export function CallbackPage() {
  const { handleCallback } = useAuth();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [status, setStatus] = useState<'loading' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const processCallback = async () => {
      const code = searchParams.get('code');
      const state = searchParams.get('state');
      const error = searchParams.get('error');
      const errorDescription = searchParams.get('error_description');

      // Handle OAuth error from Keycloak
      if (error) {
        setStatus('error');
        setErrorMessage(
          errorDescription || `Authentication failed: ${error}`
        );
        return;
      }

      // Validate required parameters
      if (!code || !state) {
        setStatus('error');
        setErrorMessage('Invalid callback: missing required parameters');
        return;
      }

      // Server-side PKCE/state is authoritative (stored in Redis and consumed
      // atomically by the backend). The frontend does not validate a locally-
      // stored PKCE state. Proceed if code and state are present.

      try {
        await handleCallback(code, state);
        // Success - handleCallback will redirect
      } catch (err) {
        setStatus('error');
        setErrorMessage(
          err instanceof Error ? err.message : 'Authentication failed'
        );
      }
    };

    processCallback();
  }, [searchParams, handleCallback, navigate]);

  const handleRetry = () => {
    navigate('/login');
  };

  if (status === 'error') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 px-4">
        <div className="max-w-md w-full">
          <ErrorState
            title="Authentication Failed"
            message={errorMessage || 'An error occurred during authentication'}
            onRetry={handleRetry}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 px-4">
      <div className="max-w-md w-full">
        <LoadingState message="Completing sign in..." />
      </div>
    </div>
  );
}
