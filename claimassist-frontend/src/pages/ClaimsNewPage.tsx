import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import { LoadingState } from '../components/ui/LoadingState';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';

interface Policy {
  id: number;
  policyNumber?: string;
  product?: string;
  status?: string;
}

export default function ClaimsNewPage(): React.ReactElement {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [step, setStep] = useState(1);
  const [loadingPolicies, setLoadingPolicies] = useState(true);
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [policyError, setPolicyError] = useState<string | null>(null);

  const [form, setForm] = useState({
    policyId: 0 as number,
    incidentType: '',
    incidentDate: '', // ISO date
    estimatedAmountCents: undefined as number | undefined,
  });

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  // successId reserved for future success UI; not used immediately
  const [_successId, setSuccessId] = useState<number | null>(null);

  useEffect(() => {
    const loadPolicies = async () => {
      setLoadingPolicies(true);
      setPolicyError(null);
      try {
        const data = await apiClient.get<Policy[]>(API_ENDPOINTS.POLICIES_ALL);
        setPolicies(data || []);
      } catch (err) {
        setPolicyError('Failed to load policies');
      } finally {
        setLoadingPolicies(false);
      }
    };

    loadPolicies();
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
      if (d > now) return 'Incident date cannot be in the future';
    }
    return null;
  };

  const next = () => {
    const err = validateStep(step);
    if (err) {
      setSubmitError(err);
      return;
    }
    setSubmitError(null);
    setStep(prev => prev + 1);
  };

  const back = () => {
    setSubmitError(null);
    setStep(prev => Math.max(1, prev - 1));
  };

  const cancel = () => {
    navigate('/claims');
  };

  const onSubmit = async () => {
    const err = validateStep(step);
    if (err) {
      setSubmitError(err);
      return;
    }

    if (!user?.customerId) {
      setSubmitError('No authenticated user');
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

      const resp = await apiClient.post<any>(API_ENDPOINTS.CLAIMS, payload);
      // Expect ClaimResponse { id, claimNumber, status, incidentType }
      setSuccessId(resp.id ?? null);
      // Navigate to the new claim if possible
      if (resp?.id) {
        navigate(`/claims/${resp.id}`);
      }
    } catch (err: any) {
      setSubmitError(err?.message || 'Failed to submit claim');
    } finally {
      setSubmitting(false);
    }
  };

  if (loadingPolicies) return <LoadingState message="Loading policies..." />;
  if (policyError) return <ErrorState message={policyError} />;
  if (!policies || policies.length === 0) return <EmptyState title="No policies available" description="You have no active policies to file a claim against." action={null} />;

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">File a Claim</h1>

      <div className="mb-4">Step {step} of 3</div>

      {step === 1 && (
        <div>
          <h2 className="text-lg font-medium">Select Policy</h2>
          <p className="text-sm text-[var(--color-text-secondary)] mb-3">Choose the policy this claim is for.</p>
          <div className="space-y-2">
            {policies.map(p => (
              <label key={p.id} className={`block p-3 border rounded ${form.policyId === p.id ? 'border-blue-500 bg-blue-50' : 'border-gray-200'}`}>
                <input type="radio" name="policy" value={p.id} checked={form.policyId === p.id} onChange={() => setForm({ ...form, policyId: p.id })} />
                <span className="ml-2 font-medium">{p.policyNumber ?? `Policy ${p.id}`}</span>
                <div className="text-sm">{p.product ?? ''} — {p.status ?? ''}</div>
              </label>
            ))}
          </div>
        </div>
      )}

      {step === 2 && (
        <div>
          <h2 className="text-lg font-medium">Incident Details</h2>
          <p className="text-sm text-[var(--color-text-secondary)] mb-3">When and what happened?</p>

          <div className="mb-3">
            <label className="block text-sm font-medium">Incident type</label>
            <input className="mt-1 block w-full rounded border p-2" value={form.incidentType} onChange={e => setForm({ ...form, incidentType: e.target.value })} aria-label="Incident type" />
          </div>

          <div className="mb-3">
            <label className="block text-sm font-medium">Incident date</label>
            <input type="date" className="mt-1 block rounded border p-2" value={form.incidentDate} onChange={e => setForm({ ...form, incidentDate: e.target.value })} aria-label="Incident date" />
          </div>

          <div className="mb-3">
            <label className="block text-sm font-medium">Estimated amount (optional)</label>
            <input type="number" className="mt-1 block rounded border p-2" value={form.estimatedAmountCents ?? ''} onChange={e => setForm({ ...form, estimatedAmountCents: e.target.value ? Math.round(Number(e.target.value) * 100) : undefined })} aria-label="Estimated amount" />
            <div className="text-xs text-[var(--color-text-secondary)]">Enter amount in dollars (will be converted to cents).</div>
          </div>
        </div>
      )}

      {step === 3 && (
        <div>
          <h2 className="text-lg font-medium">Review</h2>
          <dl className="mt-3 space-y-2">
            <div>
              <dt className="text-sm font-medium">Policy</dt>
              <dd className="text-sm">{policies.find(p => p.id === form.policyId)?.policyNumber ?? form.policyId}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium">Incident type</dt>
              <dd className="text-sm">{form.incidentType}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium">Incident date</dt>
              <dd className="text-sm">{form.incidentDate}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium">Estimated amount</dt>
              <dd className="text-sm">{form.estimatedAmountCents ? `$${(form.estimatedAmountCents/100).toFixed(2)}` : '—'}</dd>
            </div>
          </dl>
        </div>
      )}

      {submitError && <div className="mt-4 text-red-600">{submitError}</div>}

      <div className="mt-6 flex gap-2">
        <button className="btn" onClick={cancel} disabled={submitting}>Cancel</button>
        {step > 1 && <button className="btn" onClick={back} disabled={submitting}>Back</button>}
        {step < 3 && <button className="btn btn-primary" onClick={next} disabled={submitting}>Next</button>}
        {step === 3 && <button className="btn btn-primary" onClick={onSubmit} disabled={submitting}>{submitting ? 'Submitting...' : 'Submit Claim'}</button>}
      </div>
    </div>
  );
}
