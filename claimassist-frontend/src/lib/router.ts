import { useEffect, useState, useCallback } from 'react';

export type Route = {
  path: string;
  params: Record<string, string>;
};

/**
 * Parse the current location into a Route.
 *
 * Priority:
 * 1. If a hash is present (e.g. #/dashboard) use hash-based routing (preserve existing behaviour)
 * 2. Otherwise, fall back to pathname-based routing so top-level redirects like /dashboard and /callback work
 */
function parseRoute(): Route {
  // If the top-level pathname indicates an OAuth callback, prefer it even
  // if a hash fragment is present. This prevents the hash router from
  // transforming /callback before the canonical callback handler runs.
  const pathname = window.location.pathname || '/';
  const href = window.location.href || '';
  if (pathname === '/callback' || href.includes('/callback?')) return { path: '/callback', params: {} };

  const rawHash = window.location.hash.replace(/^#/, '');
  const hasHash = rawHash !== '' && rawHash !== '/';

  const params: Record<string, string> = {};

  if (hasHash) {
    const parts = rawHash.split('/').filter(Boolean);

    // /claim/:id
    if (parts[0] === 'claim' && parts[1]) {
      params.id = parts[1];
      return { path: '/claim/:id', params };
    }
    // /policy/:id
    if (parts[0] === 'policy' && parts[1]) {
      params.id = parts[1];
      return { path: '/policy/:id', params };
    }

    return { path: '/' + parts.join('/'), params };
  }

  // No hash present - use pathname. This ensures backend redirects to /dashboard
  // or other top-level routes are recognized.

  const parts = pathname.replace(/^\//, '').split('/').filter(Boolean);

  if (parts.length === 0) return { path: '/', params };

  if (parts[0] === 'claim' && parts[1]) {
    params.id = parts[1];
    return { path: '/claim/:id', params };
  }

  if (parts[0] === 'policy' && parts[1]) {
    params.id = parts[1];
    return { path: '/policy/:id', params };
  }

  return { path: '/' + parts.join('/'), params };
}

export function useRouter() {
  const [route, setRoute] = useState<Route>(parseRoute());

  useEffect(() => {
    const onChange = () => setRoute(parseRoute());
    // Listen to hash changes for normal SPA navigation
    window.addEventListener('hashchange', onChange);
    // Listen to popstate for pathname changes (OAuth callback, backend redirects)
    window.addEventListener('popstate', onChange);
    return () => {
      window.removeEventListener('hashchange', onChange);
      window.removeEventListener('popstate', onChange);
    };
  }, []);

  const navigate = useCallback((to: string) => {
    // Preserve existing behaviour: navigation uses hash to keep SPA-style URLs
    window.location.hash = to;
    window.scrollTo(0, 0);
  }, []);

  return { route, navigate };
}

export function navigate(to: string) {
  window.location.hash = to;
  window.scrollTo(0, 0);
}
