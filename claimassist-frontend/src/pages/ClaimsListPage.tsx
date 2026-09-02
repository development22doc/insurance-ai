import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import type { ClaimSummaryResponse } from '../types';
import { EmptyState, ErrorState, LoadingState } from '../components/ui';

const STATUS_STYLES: Record<string, string> = {
  SUBMITTED: 'border border-sky-200 bg-sky-50 text-sky-800',
  UNDER_REVIEW: 'border border-amber-200 bg-amber-50 text-amber-800',
  DOCS_REQUESTED: 'border border-violet-200 bg-violet-50 text-violet-800',
  APPROVED: 'border border-emerald-200 bg-emerald-50 text-emerald-800',
  DENIED: 'border border-red-200 bg-red-50 text-red-800',
  PAID: 'border border-indigo-200 bg-indigo-50 text-indigo-800',
  CLOSED: 'border border-slate-200 bg-slate-100 text-slate-700',
};

const formatDate = (value?: string | number | null) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString();
};

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
        const response = await apiClient.get<ClaimSummaryResponse[]>(API_ENDPOINTS.CLAIMS);
        if (mounted) setClaims(response || []);
      } catch (err) {
        if (mounted) setError(err instanceof Error ? err.message : String(err));
      } finally {
        if (mounted) setLoading(false);
      }
    }

    void load();
    return () => { mounted = false; };
  }, []);

  if (loading) return <LoadingState message="Loading claims..." />;
  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />;

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">Claims</h1>
        <Link to="/claims/new" className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-white">
          File a Claim
        </Link>
      </header>

      {claims && claims.length > 0 ? (
        <div className="space-y-3">
          {claims.map((claim) => (
            <div key={claim.id} className="rounded-lg border border-[var(--color-border)] bg-[var(--color-background)] p-4">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <div className="font-semibold">{claim.claimNumber}</div>
                  <div className="text-sm text-[var(--color-text-secondary)]">
                    {claim.incidentType} • {formatDate(claim.incidentDate)}
                  </div>
                  <div className="mt-1 text-xs text-[var(--color-text-secondary)]">Policy: {claim.policyId ?? '—'}</div>
                </div>
                <div className="flex items-center gap-3">
                  <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_STYLES[claim.status] ?? 'border border-slate-200 bg-slate-100 text-slate-700'}`}>
                    {claim.status}
                  </span>
                  <Link to={`/claims/${encodeURIComponent(String(claim.id))}`} className="text-sm font-medium text-[var(--color-primary)]">
                    View Claim
                  </Link>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState
          title="No claims"
          description="You have no claims at this time."
          action={<Link to="/claims/new" className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-white">File a Claim</Link>}
        />
      )}
    </div>
  );
};

export default ClaimsListPage;
