import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, ErrorState, LoadingState, Modal } from '../components/ui';
import { API_ENDPOINTS } from '../config/api';
import { useAuth } from '../contexts/AuthContext';
import { apiClient } from '../services/api-client';
import type { ClaimSummaryResponse } from '../types';

const STATUS_LABELS: Record<string, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  DOCS_REQUESTED: 'Documents requested',
  APPROVED: 'Approved',
  DENIED: 'Denied',
  PAID: 'Paid',
  CLOSED: 'Closed',
};

const TRANSITION_LABELS: Record<string, string> = {
  UNDER_REVIEW: 'Move to Under Review',
  DOCS_REQUESTED: 'Request documents',
  APPROVED: 'Approve claim',
  DENIED: 'Deny claim',
  PAID: 'Mark paid',
  CLOSED: 'Close claim',
};

const VALID_TRANSITIONS: Record<string, string[]> = {
  SUBMITTED: ['UNDER_REVIEW'],
  UNDER_REVIEW: ['DOCS_REQUESTED', 'APPROVED', 'DENIED'],
  DOCS_REQUESTED: ['UNDER_REVIEW'],
  APPROVED: ['PAID'],
  DENIED: ['CLOSED'],
  PAID: ['CLOSED'],
  CLOSED: [],
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

export const OperationsClaimWorkspacePage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [claim, setClaim] = useState<ClaimSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [pendingStatus, setPendingStatus] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const isAdjuster = hasRole('ADJUSTER');

  const loadClaim = async (silent = false) => {
    if (!id) {
      setError('Claim ID is missing');
      setLoading(false);
      return;
    }

    if (!silent) {
      setLoading(true);
    }
    setError(null);
    setSubmitError(null);

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
        setError(message || 'Unable to load claim workspace.');
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    void loadClaim();
  }, [id]);

  const allowedTransitions = useMemo(() => {
    if (!claim?.status) return [];
    return VALID_TRANSITIONS[claim.status] ?? [];
  }, [claim?.status]);

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadClaim(true);
  };

  const handleConfirm = async () => {
    if (!id || !pendingStatus || !claim) return;

    setSubmitting(true);
    setSubmitError(null);

    try {
      await apiClient.patch<ClaimSummaryResponse>(API_ENDPOINTS.CLAIM_UPDATE_STATUS(Number(id)), {
        status: pendingStatus,
        note: note.trim() || undefined,
      });

      setPendingStatus(null);
      setNote('');
      await loadClaim(true);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Unable to update claim status.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <LoadingState message="Loading claim workspace..." />;
  }

  if (error && !claim) {
    return <ErrorState message={error} onRetry={() => void loadClaim()} />;
  }

  if (!claim) {
    return (
      <div className="space-y-6">
        <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-6">
          <h1 className="text-2xl font-bold">Claim not found</h1>
          <p className="mt-2 text-[var(--color-text-secondary)]">We couldn&apos;t find this claim in the current operations view.</p>
          <button
            type="button"
            onClick={() => navigate('/operations/claims')}
            className="mt-4 rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-white"
          >
            Back to queue
          </button>
        </div>
      </div>
    );
  }

  const statusLabel = STATUS_LABELS[claim.status] ?? claim.status;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-5 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.2em] text-[var(--color-text-secondary)]">Operations workspace</p>
          <h1 className="mt-1 text-3xl font-bold text-[var(--color-text-primary)]">{claim.claimNumber}</h1>
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            {claim.incidentType} • {claim.policyId ? `Policy ${claim.policyId}` : 'Policy unavailable'}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <span className="inline-flex rounded-full border border-[var(--color-border)] bg-white px-3 py-2 text-sm font-semibold text-[var(--color-text-primary)]">
            {statusLabel}
          </span>
          <button
            type="button"
            onClick={() => void handleRefresh()}
            disabled={refreshing}
            className="rounded-lg border border-[var(--color-border)] px-3 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-60"
          >
            {refreshing ? 'Refreshing...' : 'Refresh status'}
          </button>
          <Link to="/operations/claims" className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-white">
            Back to queue
          </Link>
        </div>
      </header>

      {submitError && (
        <Alert variant="danger">{submitError}</Alert>
      )}

      <div className="grid gap-6 xl:grid-cols-[1.5fr_0.9fr]">
        <section className="space-y-6">
          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-5">
            <h2 className="text-xl font-semibold">Claim overview</h2>
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
                <dt className="text-sm text-[var(--color-text-secondary)]">Status</dt>
                <dd className="mt-1 font-medium">{statusLabel}</dd>
              </div>
              <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
                <dt className="text-sm text-[var(--color-text-secondary)]">Incident Type</dt>
                <dd className="mt-1 font-medium">{claim.incidentType}</dd>
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
                <dt className="text-sm text-[var(--color-text-secondary)]">Policy ID</dt>
                <dd className="mt-1 font-medium">{claim.policyId ?? '—'}</dd>
              </div>
              <div className="rounded-lg border border-[var(--color-border)] bg-white p-3">
                <dt className="text-sm text-[var(--color-text-secondary)]">Estimated Amount</dt>
                <dd className="mt-1 font-medium">{formatCurrency(claim.estimatedAmountCents)}</dd>
              </div>
              <div className="rounded-lg border border-[var(--color-border)] bg-white p-3 sm:col-span-2">
                <dt className="text-sm text-[var(--color-text-secondary)]">Approved Amount</dt>
                <dd className="mt-1 font-medium">{formatCurrency(claim.approvedAmountCents)}</dd>
              </div>
            </dl>
          </div>

          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-5">
            <h2 className="text-xl font-semibold">Claim status rules</h2>
            <p className="mt-3 text-sm text-[var(--color-text-secondary)]">
              Backend-supported status transitions are limited to the claim state machine. This workspace only exposes transitions the service actually allows for the current claim.
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              {Object.keys(VALID_TRANSITIONS).map((statusKey) => (
                <span
                  key={statusKey}
                  className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${claim.status === statusKey ? 'border-[var(--color-primary)] bg-[var(--color-primary)] text-white' : 'border-[var(--color-border)] bg-white text-[var(--color-text-secondary)]'}`}
                >
                  {statusKey}
                </span>
              ))}
            </div>
          </div>
        </section>

        <aside className="space-y-6">
          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-5">
            <h2 className="text-xl font-semibold">Decision panel</h2>

            {!isAdjuster ? (
              <Alert variant="info">
                You are viewing this claim in read-only mode. Adjusters are the role authorized to update claim status.
              </Alert>
            ) : allowedTransitions.length === 0 ? (
              <p className="mt-4 text-sm text-[var(--color-text-secondary)]">
                There are no supported status transitions available for this claim right now.
              </p>
            ) : (
              <div className="mt-4 space-y-3">
                {allowedTransitions.map((nextStatus) => (
                  <button
                    key={nextStatus}
                    type="button"
                    onClick={() => setPendingStatus(nextStatus)}
                    className="w-full rounded-lg border border-[var(--color-border)] bg-white px-4 py-3 text-left transition-colors hover:border-[var(--color-primary)] hover:bg-[var(--color-surface)]"
                  >
                    <div className="font-semibold text-[var(--color-text-primary)]">{TRANSITION_LABELS[nextStatus] ?? nextStatus}</div>
                    <div className="mt-1 text-sm text-[var(--color-text-secondary)]">
                      {statusLabel} → {STATUS_LABELS[nextStatus] ?? nextStatus}
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-5">
            <h2 className="text-xl font-semibold">Operational limits</h2>
            <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-[var(--color-text-secondary)]">
              <li>Document upload and download are not exposed to the browser by the current backend contract.</li>
              <li>AI assessment is not surfaced here because the browser-facing API is not exposed publicly.</li>
              <li>Claim history is stored server-side; no browser-facing audit endpoint is available in this phase.</li>
            </ul>
          </div>
        </aside>
      </div>

      <Modal
        isOpen={Boolean(pendingStatus)}
        onClose={() => {
          setPendingStatus(null);
          setNote('');
        }}
        title={`Confirm ${TRANSITION_LABELS[pendingStatus ?? ''] ?? pendingStatus ?? 'status update'}`}
        size="md"
      >
        <div className="space-y-4">
          <p className="text-sm text-[var(--color-text-secondary)]">
            Update claim <span className="font-semibold text-[var(--color-text-primary)]">{claim.claimNumber}</span> from <span className="font-semibold text-[var(--color-text-primary)]">{statusLabel}</span> to <span className="font-semibold text-[var(--color-text-primary)]">{STATUS_LABELS[pendingStatus ?? ''] ?? pendingStatus}</span>.
          </p>

          <label className="block text-sm font-medium text-[var(--color-text-primary)]" htmlFor="claim-status-note">
            Optional note
          </label>
          <textarea
            id="claim-status-note"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            rows={4}
            className="w-full rounded-lg border border-[var(--color-border)] bg-white px-3 py-2 text-sm focus:border-[var(--color-primary)] focus:outline-none"
            placeholder="Add context for this status change..."
          />

          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => {
                setPendingStatus(null);
                setNote('');
              }}
              className="rounded-lg border border-[var(--color-border)] px-4 py-2 text-sm font-medium"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void handleConfirm()}
              disabled={submitting}
              className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitting ? 'Updating...' : 'Confirm'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default OperationsClaimWorkspacePage;
