import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import type { PolicyResponse } from '../types';
import { LoadingState, ErrorState, EmptyState } from '../components/ui';

export const PolicyDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [policy, setPolicy] = useState<PolicyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    async function load() {
      if (!id) {
        setError('Policy ID is missing');
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);

      try {
        const pid = Number(id);
        // Call backend detail endpoint
        const res = await apiClient.get<PolicyResponse>(API_ENDPOINTS.POLICY_BY_ID(pid));
        if (mounted) setPolicy(res);
      } catch (err: any) {
        if (mounted) {
          if (err instanceof Error && /404/.test(err.message)) {
            setPolicy(null);
            setError(null);
          } else {
            setError(err instanceof Error ? err.message : String(err));
          }
        }
      } finally {
        if (mounted) setLoading(false);
      }
    }

    load();
    return () => { mounted = false; };
  }, [id]);

  if (loading) return <LoadingState message="Loading policy..." />;
  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />;
  if (!policy) return <EmptyState title="Policy not found" description="We couldn't find that policy." action={<button onClick={() => navigate(-1)} className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg">Back to policies</button>} />;

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Policy {policy.policyNumber}</h1>
          <p className="text-sm text-[var(--color-text-secondary)]">{policy.productType} • {policy.coveragePlanName}</p>
        </div>
        <div className="text-sm font-semibold">{policy.status}</div>
      </header>

      <section aria-labelledby="overview-heading">
        <h2 id="overview-heading" className="text-lg font-semibold">Overview</h2>
        <div className="mt-3 bg-[var(--color-background)] border border-[var(--color-border)] p-4 rounded-lg">
          <div><strong>Policy Number:</strong> {policy.policyNumber}</div>
          <div><strong>Status:</strong> {policy.status}</div>
          <div><strong>Product:</strong> {policy.productType}</div>
          <div><strong>Coverage:</strong> {policy.coveragePlanName}</div>
        </div>
      </section>

      <section aria-labelledby="dates-heading">
        <h2 id="dates-heading" className="text-lg font-semibold">Dates</h2>
        <div className="mt-3 bg-[var(--color-background)] border border-[var(--color-border)] p-4 rounded-lg">
          <div><strong>Effective:</strong> {policy.effectiveDate}</div>
          <div><strong>Renewal:</strong> {policy.renewalDate ?? '—'}</div>
        </div>
      </section>

      <div>
        <Link to="/policies" className="text-[var(--color-primary)]">Back to policies</Link>
      </div>
    </div>
  );
};

export default PolicyDetailsPage;
