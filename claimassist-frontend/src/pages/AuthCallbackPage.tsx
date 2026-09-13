import React, { useEffect, useState } from 'react';

// Canonical OAuth callback page. Reads code/state from the top-level browser
// URL and performs a top-level navigation to the Gateway callback endpoint so
// the backend can complete the authorization code exchange and set HttpOnly
// cookies. This component must NOT depend on the hash router and must not
// perform any fetch() or context-driven navigation while the callback is
// being processed.

export function AuthCallbackPage(): JSX.Element {
  const [status, setStatus] = useState<'processing' | 'error'>('processing');
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    try {
      const u = new URL(window.location.href);
      // Prefer the real top-level search portion
      let search = u.search || '';

      // Fallback: if search is empty, extract substring after first '?' in the href
      if (!search) {
        const href = window.location.href || '';
        const q = href.indexOf('?');
        if (q >= 0) search = href.substring(q);
      }

      const params = new URLSearchParams(search);
      const code = params.get('code');
      const state = params.get('state');

      if (code && state) {
        // Use API_BASE from environment (defaults to localhost:8080 if not set)
        const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
        const gatewayCallbackUrl = `${apiBase}/customer/auth/callback?code=${encodeURIComponent(
          code
        )}&state=${encodeURIComponent(state)}`;
        // Top-level navigation -- do not use fetch()
        window.location.assign(gatewayCallbackUrl);
        return;
      }

      setStatus('error');
      setMessage('Authentication callback parameters missing. Please sign in again.');
    } catch (e) {
      setStatus('error');
      setMessage('Failed to process authentication callback.');
    }
  }, []);

  if (status === 'processing') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 px-4">
        <div className="max-w-md w-full text-center">
          <h2 className="text-xl font-semibold">Completing sign in...</h2>
          <p className="mt-3 text-sm text-slate-600">Please wait while we complete your sign in.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 px-4">
      <div className="max-w-md w-full">
        <div className="bg-white shadow rounded-lg p-6 text-center">
          <h3 className="text-lg font-semibold">Authentication Error</h3>
          <p className="mt-2 text-sm text-slate-600">{message}</p>
          <div className="mt-4">
            <a href="/" className="text-sm text-blue-600 hover:underline">Back to home</a>
          </div>
        </div>
      </div>
    </div>
  );
}

export default AuthCallbackPage;

