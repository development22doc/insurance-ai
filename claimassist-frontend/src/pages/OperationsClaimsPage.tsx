import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { EmptyState, ErrorState, LoadingState } from '../components/ui';
import { API_ENDPOINTS } from '../config/api';
import { apiClient } from '../services/api-client';
import type { ClaimSummaryResponse } from '../types';

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

export const OperationsClaimsPage: React.FC = () => {
  const [claims, setClaims] = useState<ClaimSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadClaims = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await apiClient.get<ClaimSummaryResponse[]>(API_ENDPOINTS.CLAIMS);
      setClaims(response || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load claims queue.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadClaims();
  }, []);

  if (loading) {
    return <LoadingState message="Loading claims queue..." />;
  }

  if (error) {
    return <ErrorState message={error} onRetry={() => void loadClaims()} />;
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-3xl font-bold text-[var(--color-text-primary)]">Claims queue</h1>
      </header>

      {claims.length === 0 ? (
        <EmptyState
          title="Queue is empty"
          description="There are no claims in the current queue."
        />
      ) : (
        <>
          <div className="hidden md:block overflow-x-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-background)]">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-[var(--color-surface)] text-[var(--color-text-secondary)]">
                <tr>
                  <th className="px-4 py-3 font-medium">Claim</th>
                  <th className="px-4 py-3 font-medium">Incident type</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Incident date</th>
                  <th className="px-4 py-3 font-medium">Created</th>
                  <th className="px-4 py-3 font-medium text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {claims.map((claim) => (
                  <tr key={claim.id} className="border-t border-[var(--color-border)] bg-white">
                    <td className="px-4 py-3 font-medium text-[var(--color-text-primary)]">{claim.claimNumber}</td>
                    <td className="px-4 py-3">{claim.incidentType}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_STYLES[claim.status] ?? 'border border-slate-200 bg-slate-100 text-slate-700'}`}>
                        {claim.status}
                      </span>
                    </td>
                    <td className="px-4 py-3">{formatDate(claim.incidentDate)}</td>
                    <td className="px-4 py-3">{formatDate(claim.createdAt)}</td>
                    <td className="px-4 py-3 text-right">
                      <Link to={`/operations/claims/${claim.id}`} className="font-medium text-[var(--color-primary)]">
                        View Claim
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="space-y-3 md:hidden">
            {claims.map((claim) => (
              <div key={claim.id} className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="font-semibold text-[var(--color-text-primary)]">{claim.claimNumber}</div>
                    <div className="text-sm text-[var(--color-text-secondary)]">{claim.incidentType}</div>
                  </div>
                  <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_STYLES[claim.status] ?? 'border border-slate-200 bg-slate-100 text-slate-700'}`}>
                    {claim.status}
                  </span>
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <dt className="text-[var(--color-text-secondary)]">Incident date</dt>
                    <dd className="font-medium">{formatDate(claim.incidentDate)}</dd>
                  </div>
                  <div>
                    <dt className="text-[var(--color-text-secondary)]">Created</dt>
                    <dd className="font-medium">{formatDate(claim.createdAt)}</dd>
                  </div>
                </dl>
                <div className="mt-4">
                  <Link to={`/operations/claims/${claim.id}`} className="inline-flex rounded-lg bg-[var(--color-primary)] px-3 py-2 text-sm font-medium text-white">
                    View Claim
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
};

export default OperationsClaimsPage;
