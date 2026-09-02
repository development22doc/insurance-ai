import React, { useEffect, useMemo, useState } from 'react';
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

const formatCurrency = (value?: number | null) => {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(value / 100);
};

export const OperationsDashboardPage: React.FC = () => {
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
      setError(err instanceof Error ? err.message : 'Unable to load operations dashboard.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadClaims();
  }, []);

  const stats = useMemo(() => {
    const totals = {
      visibleClaims: claims.length,
      submitted: 0,
      underReview: 0,
      docsRequested: 0,
      approved: 0,
      denied: 0,
      paid: 0,
      closed: 0,
    };

    for (const claim of claims) {
      switch (claim.status) {
        case 'SUBMITTED':
          totals.submitted += 1;
          break;
        case 'UNDER_REVIEW':
          totals.underReview += 1;
          break;
        case 'DOCS_REQUESTED':
          totals.docsRequested += 1;
          break;
        case 'APPROVED':
          totals.approved += 1;
          break;
        case 'DENIED':
          totals.denied += 1;
          break;
        case 'PAID':
          totals.paid += 1;
          break;
        case 'CLOSED':
          totals.closed += 1;
          break;
        default:
          break;
      }
    }

    return totals;
  }, [claims]);

  const recentClaims = useMemo(
    () => [...claims].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 5),
    [claims]
  );

  if (loading) {
    return <LoadingState message="Loading operations dashboard..." />;
  }

  if (error) {
    return <ErrorState message={error} onRetry={() => void loadClaims()} />;
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.2em] text-[var(--color-text-secondary)]">Operations</p>
          <h1 className="mt-1 text-3xl font-bold text-[var(--color-text-primary)]">Operations dashboard</h1>
          <p className="mt-2 text-sm text-[var(--color-text-secondary)]">
            Review the current claim queue and monitor the latest claim activity in the backend-backed operations view.
          </p>
        </div>
        <div className="rounded-full border border-[var(--color-border)] bg-[var(--color-background)] px-3 py-1.5 text-sm text-[var(--color-text-secondary)]">
          {stats.visibleClaims} visible claims
        </div>
      </header>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {[
          { label: 'Submitted', value: stats.submitted },
          { label: 'Under review', value: stats.underReview },
          { label: 'Docs requested', value: stats.docsRequested },
          { label: 'Approved', value: stats.approved },
          { label: 'Denied', value: stats.denied },
          { label: 'Paid', value: stats.paid },
          { label: 'Closed', value: stats.closed },
        ].map((item) => (
          <div key={item.label} className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-4">
            <p className="text-sm text-[var(--color-text-secondary)]">{item.label}</p>
            <p className="mt-3 text-3xl font-bold text-[var(--color-text-primary)]">{item.value}</p>
          </div>
        ))}
      </section>

      <section className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-5">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-xl font-semibold">Recent claims</h2>
          <Link to="/operations/claims" className="text-sm font-medium text-[var(--color-primary)]">
            View queue
          </Link>
        </div>

        {claims.length === 0 ? (
          <EmptyState
            title="No claims available"
            description="There are no claims in the current operations view yet."
          />
        ) : (
          <div className="space-y-3">
            {recentClaims.map((claim) => (
              <div key={claim.id} className="flex flex-col gap-3 rounded-lg border border-[var(--color-border)] bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <div className="font-semibold text-[var(--color-text-primary)]">{claim.claimNumber}</div>
                  <div className="text-sm text-[var(--color-text-secondary)]">
                    {claim.incidentType} • {formatDate(claim.incidentDate)}
                  </div>
                  <div className="mt-1 text-xs text-[var(--color-text-secondary)]">
                    Created {formatDate(claim.createdAt)} • {formatCurrency(claim.estimatedAmountCents)} est.
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_STYLES[claim.status] ?? 'border border-slate-200 bg-slate-100 text-slate-700'}`}>
                    {claim.status}
                  </span>
                  <Link to={`/operations/claims/${claim.id}`} className="text-sm font-medium text-[var(--color-primary)]">
                    Open
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
};

export default OperationsDashboardPage;
