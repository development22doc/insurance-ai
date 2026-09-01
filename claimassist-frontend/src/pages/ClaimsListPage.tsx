import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import type { ClaimSummaryResponse } from '../types';
import { LoadingState, EmptyState, ErrorState } from '../components/ui';

export const ClaimsListPage: React.FC = () => {
  const [claims, setClaims] = useState<ClaimSummaryResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const res = await apiClient.get<ClaimSummaryResponse[]>(API_ENDPOINTS.CLAIMS);
        if (mounted) setClaims(res || []);
      } catch (err) {
        if (mounted) setError(err instanceof Error ? err.message : String(err));
      } finally {
        if (mounted) setLoading(false);
      }
    }
    load();
    return () => { mounted = false; };
  }, []);

  if (loading) return <LoadingState message="Loading claims..." />;
  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />;

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Claims</h1>
        <Link to="/claims/new" className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg">File a Claim</Link>
      </header>

      {claims && claims.length > 0 ? (
        <div className="space-y-3">
          {claims.map(c => (
            <div key={c.id} className="bg-[var(--color-background)] border border-[var(--color-border)] p-3 rounded-lg flex items-center justify-between">
              <div>
                <div className="font-medium">{c.claimNumber} — {c.incidentType}</div>
                <div className="text-sm text-[var(--color-text-secondary)]">{new Date(c.incidentDate).toLocaleDateString()} • Policy: {c.policyId ?? '—'}</div>
              </div>
              <div className="flex items-center gap-4">
                <div className="text-sm font-semibold">{c.status}</div>
                <Link to={`/claims/${encodeURIComponent(String(c.id))}`} className="text-[var(--color-primary)]">View Claim</Link>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState
          title="No claims"
          description="You have no claims at this time."
          action={<Link to="/products" className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg">Explore Products</Link>}
        />
      )}
    </div>
  );
};

export default ClaimsListPage;
