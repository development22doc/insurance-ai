import React, { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, EmptyState, ErrorState, LoadingState } from '../components/ui';
import { API_ENDPOINTS } from '../config/api';
import { apiClient } from '../services/api-client';
import type { ClaimSummaryResponse } from '../types';
import AIAssistant from '../components/AIAssistant';

const STATUS_STYLES: Record<string, string> = {
  SUBMITTED: 'border border-sky-200 bg-sky-50 text-sky-800',
  UNDER_REVIEW: 'border border-amber-200 bg-amber-50 text-amber-800',
  DOCS_REQUESTED: 'border border-violet-200 bg-violet-50 text-violet-800',
  APPROVED: 'border border-emerald-200 bg-emerald-50 text-emerald-800',
  DENIED: 'border border-red-200 bg-red-50 text-red-800',
  PAID: 'border border-indigo-200 bg-indigo-50 text-indigo-800',
  CLOSED: 'border border-slate-200 bg-slate-100 text-slate-700',
};

const formatCurrency = (value?: number | null) => {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(value / 100);
};

const formatDate = (value?: string | number | null) => {
  if (!value) return '—';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';

  return date.toLocaleString();
};

interface ClaimDetailsPageProps {
  backLink?: string;
  backLabel?: string;
}

export const ClaimDetailsPage: React.FC<ClaimDetailsPageProps> = ({
  backLink = '/claims',
  backLabel = 'Back to claims',
}) => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [claim, setClaim] = useState<ClaimSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadClaim = useCallback(async (silent = false) => {
    if (!id) {
      setError('Claim ID is missing');
      setLoading(false);
      return;
    }

    if (!silent) setLoading(true);
    setError(null);

    try {
      const claimId = Number(id);
      const response = await apiClient.get<ClaimSummaryResponse>(API_ENDPOINTS.CLAIM_BY_ID(claimId));
      setClaim(response);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      if (/404|not found/i.test(message)) {
        setClaim(null);
        setError('Claim not found or is no longer available.');
      } else {
        setError(message || 'Unable to load claim details.');
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [id]);

  useEffect(() => {
    void loadClaim();
  }, [loadClaim]);

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadClaim(true);
  };

  if (loading) return <LoadingState message="Loading claim..." />;
  if (error && !claim) return <ErrorState message={error} onRetry={() => void loadClaim()} />;
  if (!claim) {
    return (
      <EmptyState
        title="Claim not found"
        description="We couldn't find that claim."
        action={<button type="button" onClick={() => navigate(-1)} className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-white">Back to claims</button>}
      />
    );
  }

  const statusClass = STATUS_STYLES[claim.status] ?? 'border border-slate-200 bg-slate-100 text-slate-700';

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-sm font-medium uppercase tracking-wide text-[var(--color-text-secondary)]">Claim</p>
          <h1 className="mt-1 text-2xl font-bold">{claim.claimNumber}</h1>
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">{claim.incidentType} • Policy {claim.policyId ?? '—'}</p>
        </div>
        <div className={`inline-flex items-center rounded-full px-3 py-2 text-sm font-semibold ${statusClass}`}>
          {claim.status}
        </div>
      </header>

      <section aria-labelledby="current-status-heading" className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-5">
        <div className="flex items-center justify-between gap-3">
          <h2 id="current-status-heading" className="text-lg font-semibold">Current status</h2>
          <button
            type="button"
            onClick={() => void handleRefresh()}
            disabled={refreshing}
            className="rounded-lg border border-[var(--color-border)] px-3 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-60"
          >
            {refreshing ? 'Refreshing...' : 'Refresh Status'}
          </button>
        </div>

        <div className="mt-4 rounded-lg border border-dashed border-[var(--color-border)] bg-white p-4">
          <div className="text-sm text-[var(--color-text-secondary)]">Status</div>
          <div className={`mt-2 inline-flex rounded-full px-3 py-1 text-sm font-semibold ${statusClass}`}>
            {claim.status}
          </div>
          <p className="mt-3 text-sm text-[var(--color-text-secondary)]">
            This claim is currently tracked using the backend's real claim status.
          </p>
        </div>
      </section>

      <section aria-labelledby="claim-details-heading" className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-5">
        <h2 id="claim-details-heading" className="text-lg font-semibold">Claim details</h2>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
            <dt className="text-sm text-[var(--color-text-secondary)]">Claim Number</dt>
            <dd className="mt-1 font-medium">{claim.claimNumber}</dd>
          </div>
          <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
            <dt className="text-sm text-[var(--color-text-secondary)]">Claim ID</dt>
            <dd className="mt-1 font-medium">{claim.id}</dd>
          </div>
          <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
            <dt className="text-sm text-[var(--color-text-secondary)]">Incident Type</dt>
            <dd className="mt-1 font-medium">{claim.incidentType}</dd>
          </div>
          <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
            <dt className="text-sm text-[var(--color-text-secondary)]">Policy ID</dt>
            <dd className="mt-1 font-medium">{claim.policyId ?? '—'}</dd>
          </div>
          <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
            <dt className="text-sm text-[var(--color-text-secondary)]">Incident Date</dt>
            <dd className="mt-1 font-medium">{formatDate(claim.incidentDate)}</dd>
          </div>
          <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
            <dt className="text-sm text-[var(--color-text-secondary)]">Created Date</dt>
            <dd className="mt-1 font-medium">{formatDate(claim.createdAt)}</dd>
          </div>
          <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
            <dt className="text-sm text-[var(--color-text-secondary)]">Estimated Amount</dt>
            <dd className="mt-1 font-medium">{formatCurrency(claim.estimatedAmountCents)}</dd>
          </div>
          <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
            <dt className="text-sm text-[var(--color-text-secondary)]">Approved Amount</dt>
            <dd className="mt-1 font-medium">{formatCurrency(claim.approvedAmountCents)}</dd>
          </div>
        </dl>
      </section>

      {error && claim && (
        <Alert variant="warning">{error}</Alert>
      )}

      <AIAssistant claimId={Number(id)} />

      <div className="flex flex-wrap gap-3">
        <Link to={backLink} className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-white">
          {backLabel}
        </Link>
      </div>
    </div>
  );
};

export default ClaimDetailsPage;
