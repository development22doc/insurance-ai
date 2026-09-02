import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import type { PolicyResponse } from '../types';
import { LoadingState, EmptyState, ErrorState } from '../components/ui';

export const PoliciesListPage: React.FC = () => {
  const [policies, setPolicies] = useState<PolicyResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const res = await apiClient.get<PolicyResponse[]>(API_ENDPOINTS.POLICIES_ALL);
        if (mounted) setPolicies(res || []);
      } catch (err) {
        if (mounted) setError(err instanceof Error ? err.message : String(err));
      } finally {
        if (mounted) setLoading(false);
      }
    }
    load();
    return () => { mounted = false; };
  }, []);

  if (loading) return <LoadingState message="Loading policies..." />;
  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />;

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Policies</h1>
        <Link to="/policies/new" className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg">Create Policy</Link>
      </header>

      {policies && policies.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {policies.map(p => (
            <div key={p.id} className="bg-[var(--color-background)] border border-[var(--color-border)] p-4 rounded-lg">
              <div className="flex items-start justify-between">
                <div>
                  <h2 className="font-semibold">{p.policyNumber}</h2>
                  <p className="text-sm text-[var(--color-text-secondary)]">{p.productType} • {p.coveragePlanName}</p>
                </div>
                <div className="text-sm font-medium">{p.status}</div>
              </div>

              <div className="mt-3 text-sm text-[var(--color-text-secondary)]">
                <div>Effective: {p.effectiveDate}</div>
                <div>Renewal: {p.renewalDate ?? '—'}</div>
              </div>

              <div className="mt-4">
                <Link to={`/policies/${encodeURIComponent(String(p.id))}`} className="text-[var(--color-primary)]">View details</Link>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState
          title="No policies found"
          description="You have no policies."
          action={<Link to="/products" className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg">Explore Products</Link>}
        />
      )}
    </div>
  );
};

export default PoliciesListPage;
