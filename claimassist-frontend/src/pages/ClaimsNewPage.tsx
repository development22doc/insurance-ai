import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import { Alert, EmptyState, ErrorState, LoadingState } from '../components/ui';
import { ClaimSubmissionSuccess } from '../components/claims/ClaimSubmissionSuccess';
import {
  generateIdempotencyKey,
  getIdempotencyKey,
  storeIdempotencyKey,
} from '../lib/idempotency';
import type { ClaimResponse, PolicyResponse } from '../types';

export default function ClaimsNewPage(): React.ReactElement {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [step, setStep] = useState(1);
  const [loadingPolicies, setLoadingPolicies] = useState(true);
  const [policies, setPolicies] = useState<PolicyResponse[]>([]);
  const [policyError, setPolicyError] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);
  const [successClaim, setSuccessClaim] = useState<ClaimResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [form, setForm] = useState({
    policyId: 0 as number,
    incidentType: '',
    incidentDate: '',
    estimatedAmountCents: undefined as number | undefined,
  });

  useEffect(() => {
    let mounted = true;

    const loadPolicies = async () => {
      setLoadingPolicies(true);
      setPolicyError(null);
      try {
        const data = await apiClient.get<PolicyResponse[]>(API_ENDPOINTS.POLICIES_ALL);
        if (mounted) setPolicies(data || []);
      } catch (err) {
        if (mounted) setPolicyError(err instanceof Error ? err.message : 'Failed to load policies');
      } finally {
        if (mounted) setLoadingPolicies(false);
      }
    };

    const initializeIdempotency = () => {
      const existing = getIdempotencyKey();
      if (existing) {
        setIdempotencyKey(existing);
        return;
      }

      const nextKey = generateIdempotencyKey();
      storeIdempotencyKey(nextKey);
      setIdempotencyKey(nextKey);
    };

    loadPolicies();
    initializeIdempotency();

    return () => { mounted = false; };
  }, []);

  const validateStep = (s: number): string | null => {
    if (s === 1) {
      if (!form.policyId || form.policyId === 0) return 'Please select a policy';
    }
    if (s === 2) {
      if (!form.incidentType.trim()) return 'Incident type is required';
      if (!form.incidentDate) return 'Incident date is required';
      const d = new Date(form.incidentDate);
      const now = new Date();
      if (Number.isNaN(d.getTime())) return 'Incident date is invalid';
      if (d > now) return 'Incident date cannot be in the future';
    }
    return null;
  };

  const ensureIdempotencyKey = () => {
    const activeKey = idempotencyKey ?? getIdempotencyKey();
    if (activeKey) {
      setIdempotencyKey(activeKey);
      return activeKey;
    }

    const newKey = generateIdempotencyKey();
    storeIdempotencyKey(newKey);
    setIdempotencyKey(newKey);
    return newKey;
  };

  const next = () => {
    const err = validateStep(step);
    if (err) {
      setSubmitError(err);
      return;
    }
    setSubmitError(null);
    setStep((prev) => prev + 1);
  };

  const back = () => {
    setSubmitError(null);
    setStep((prev) => Math.max(1, prev - 1));
  };

  const cancel = () => {
    navigate('/claims');
  };

  const handleFileAnother = () => {
    setSuccessClaim(null);
    setForm({
      policyId: 0,
      incidentType: '',
      incidentDate: '',
      estimatedAmountCents: undefined,
    });
    setStep(1);
    setSubmitError(null);

    const newKey = generateIdempotencyKey();
    storeIdempotencyKey(newKey);
    setIdempotencyKey(newKey);
  };

  const onSubmit = async () => {
    if (submitting) return;

    const err = validateStep(step);
    if (err) {
      setSubmitError(err);
      return;
    }

    if (!user?.customerId) {
      setSubmitError('You must be signed in to file a claim.');
      return;
    }

    const activeKey = ensureIdempotencyKey();
    if (!activeKey) {
      setSubmitError('Unable to create a safe submission key. Please try again.');
      return;
    }

    setSubmitting(true);
    setSubmitError(null);

    try {
      const payload = {
        policyId: form.policyId,
        incidentType: form.incidentType.trim(),
        incidentDate: new Date(form.incidentDate).toISOString(),
        estimatedAmountCents: form.estimatedAmountCents ?? null,
      };

      const response = await apiClient.post<ClaimResponse>(API_ENDPOINTS.CLAIMS, payload, {
        headers: {
          'Idempotency-Key': activeKey,
        },
      });

      setSuccessClaim(response);
    } catch (submitErr) {
      const message = submitErr instanceof Error ? submitErr.message : 'Failed to submit claim';
      const normalized = /duplicate|already|idempot|409|conflict/i.test(message)
        ? 'This claim may already be submitted. Please review your claims list or track the claim status.'
        : message;
      setSubmitError(normalized);
    } finally {
      setSubmitting(false);
    }
  };

  if (loadingPolicies) return <LoadingState message="Loading policies..." />;
  if (policyError) return <ErrorState message={policyError} onRetry={() => window.location.reload()} />;
  if (!policies || policies.length === 0) {
    return (
      <EmptyState
        title="No policies available"
        description="You have no active policies to file a claim against."
      />
    );
  }

  if (successClaim) {
    return <ClaimSubmissionSuccess claim={successClaim} onFileAnother={handleFileAnother} />;
  }

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">File a Claim</h1>
      <div aria-live="polite" className="mb-4 text-sm text-[var(--color-text-secondary)]">Step {step} of 3</div>

      {step === 1 && (
        <div>
          <h2 className="text-lg font-medium">Select Policy</h2>
          <p className="text-sm text-[var(--color-text-secondary)] mb-3">Choose the policy this claim is for.</p>
          <div className="space-y-2">
            {policies.map((policy) => (
              <label
                key={policy.id}
                className={`block cursor-pointer rounded-lg border p-3 transition ${form.policyId === policy.id ? 'border-[var(--color-primary)] bg-[var(--color-primary-soft)]' : 'border-[var(--color-border)] bg-[var(--color-background)]'}`}
              >
                <div className="flex items-start gap-3">
                  <input
                    type="radio"
                    name="policy"
                    value={policy.id}
                    checked={form.policyId === policy.id}
                    onChange={() => setForm({ ...form, policyId: policy.id })}
                    aria-label={policy.policyNumber ?? `Policy ${policy.id}`}
                  />
                  <div className="flex-1">
                    <div className="font-medium">{policy.policyNumber ?? `Policy ${policy.id}`}</div>
                    <div className="text-sm text-[var(--color-text-secondary)]">
                      {policy.productType ?? policy.coveragePlanName ?? 'Policy'} • {policy.status ?? 'ACTIVE'}
                    </div>
                  </div>
                </div>
              </label>
            ))}
          </div>
        </div>
      )}

      {step === 2 && (
        <div>
          <h2 className="text-lg font-medium">Incident Details</h2>
          <p className="text-sm text-[var(--color-text-secondary)] mb-3">When and what happened?</p>

          <div className="mb-4">
            <label htmlFor="incident-type" className="block text-sm font-medium">Incident type</label>
            <input
              id="incident-type"
              className="mt-1 block w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-background)] p-3"
              value={form.incidentType}
              onChange={(event) => setForm({ ...form, incidentType: event.target.value })}
              aria-label="Incident type"
              placeholder="Accident, theft, storm damage..."
            />
          </div>

          <div className="mb-4">
            <label htmlFor="incident-date" className="block text-sm font-medium">Incident date</label>
            <input
              id="incident-date"
              type="date"
              className="mt-1 block w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-background)] p-3"
              value={form.incidentDate}
              onChange={(event) => setForm({ ...form, incidentDate: event.target.value })}
              aria-label="Incident date"
            />
          </div>

          <div className="mb-4">
            <label htmlFor="estimated-amount" className="block text-sm font-medium">Estimated amount (optional)</label>
            <input
              id="estimated-amount"
              type="number"
              min="0"
              step="0.01"
              className="mt-1 block w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-background)] p-3"
              value={
                form.estimatedAmountCents !== undefined
                  ? (form.estimatedAmountCents / 100).toString()
                  : ''
              }
              onChange={(event) => {
                const value = event.target.value;
                setForm({
                  ...form,
                  estimatedAmountCents: value ? Math.round(Number(value) * 100) : undefined,
                });
              }}
              aria-label="Estimated amount"
              placeholder="2500"
            />
            <div className="mt-1 text-xs text-[var(--color-text-secondary)]">Enter amount in dollars (it will be stored in cents).</div>
          </div>
        </div>
      )}

      {step === 3 && (
        <div>
          <h2 className="text-lg font-medium">Review</h2>
          <dl className="mt-4 space-y-3 rounded-lg border border-[var(--color-border)] bg-[var(--color-background)] p-4">
            <div>
              <dt className="text-sm font-medium text-[var(--color-text-secondary)]">Policy</dt>
              <dd className="mt-1 text-sm">{policies.find((policy) => policy.id === form.policyId)?.policyNumber ?? form.policyId}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-[var(--color-text-secondary)]">Incident type</dt>
              <dd className="mt-1 text-sm">{form.incidentType}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-[var(--color-text-secondary)]">Incident date</dt>
              <dd className="mt-1 text-sm">{form.incidentDate}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-[var(--color-text-secondary)]">Estimated amount</dt>
              <dd className="mt-1 text-sm">{form.estimatedAmountCents ? `$${(form.estimatedAmountCents / 100).toFixed(2)}` : '—'}</dd>
            </div>
          </dl>
        </div>
      )}

      {submitError && (
        <div className="mt-4">
          <Alert variant="danger">{submitError}</Alert>
        </div>
      )}

      <div className="mt-6 flex flex-wrap gap-3">
        <button type="button" className="rounded-lg border border-[var(--color-border)] px-4 py-2 text-sm font-medium" onClick={cancel} disabled={submitting}>
          Cancel
        </button>
        {step > 1 && (
          <button type="button" className="rounded-lg border border-[var(--color-border)] px-4 py-2 text-sm font-medium" onClick={back} disabled={submitting}>
            Back
          </button>
        )}
        {step < 3 && (
          <button type="button" className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60" onClick={next} disabled={submitting}>
            Next
          </button>
        )}
        {step === 3 && (
          <button
            type="button"
            className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
            onClick={onSubmit}
            disabled={submitting}
            aria-busy={submitting}
          >
            {submitting ? 'Submitting...' : 'Submit Claim'}
          </button>
        )}
      </div>
    </div>
  );
}
