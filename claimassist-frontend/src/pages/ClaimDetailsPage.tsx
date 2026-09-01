import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import type { ClaimSummaryResponse } from '../types';
import { LoadingState, ErrorState, EmptyState } from '../components/ui';

export const ClaimDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [claim, setClaim] = useState<ClaimSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    async function load() {
      if (!id) {
        setError('Claim ID is missing');
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);

      try {
        const cid = Number(id);
        const res = await apiClient.get<ClaimSummaryResponse>(API_ENDPOINTS.CLAIM_BY_ID(cid));
        if (mounted) setClaim(res);
      } catch (err: any) {
        if (mounted) {
          if (err instanceof Error && /404/.test(err.message)) {
            setClaim(null);
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

  if (loading) return <LoadingState message="Loading claim..." />;
  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />;
  if (!claim) return <EmptyState title="Claim not found" description="We couldn't find that claim." action={<button onClick={() => navigate(-1)} className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg">Back to claims</button>} />;

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{claim.claimNumber}</h1>
          <p className="text-sm text-[var(--color-text-secondary)]">{claim.incidentType} • Policy {claim.policyId ?? '—'}</p>
        </div>
        <div className="text-sm font-semibold">{claim.status}</div>
      </header>

      <section aria-labelledby="overview-heading">
        <h2 id="overview-heading" className="text-lg font-semibold">Overview</h2>
        <div className="mt-3 bg-[var(--color-background)] border border-[var(--color-border)] p-4 rounded-lg">
          <div><strong>Claim Number:</strong> {claim.claimNumber}</div>
          <div><strong>Status:</strong> {claim.status}</div>
          <div><strong>Incident:</strong> {claim.incidentType}</div>
          <div><strong>Incident Date:</strong> {new Date(claim.incidentDate).toLocaleString()}</div>
          <div><strong>Created:</strong> {new Date(claim.createdAt).toLocaleString()}</div>
        </div>
      </section>

      <div>
        <Link to="/claims" className="text-[var(--color-primary)]">Back to claims</Link>
      </div>
    </div>
  );
};

export default ClaimDetailsPage;
