import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import type { PolicyResponse, ClaimSummaryResponse, CustomerResponse } from '../types';
import { LoadingState, EmptyState, ErrorState } from '../components/ui';

export const DashboardPage: React.FC = () => {
  const { user } = useAuth();
  const [customer, setCustomer] = useState<CustomerResponse | null>(null);
  const [policies, setPolicies] = useState<PolicyResponse[] | null>(null);
  const [claims, setClaims] = useState<ClaimSummaryResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    async function load() {
      setLoading(true);
      setError(null);

      try {
        // Fetch customer profile when available
        if (user?.customerId) {
          const cust = await apiClient.get<CustomerResponse>(
            API_ENDPOINTS.CUSTOMER_UPDATE(user.customerId)
          );
          if (mounted) setCustomer(cust);
        }

        // Policies (all)
        const pols = await apiClient.get<PolicyResponse[]>(API_ENDPOINTS.POLICIES_ALL);
        if (mounted) setPolicies(pols || []);

        // Claims (my claims)
        const cls = await apiClient.get<ClaimSummaryResponse[]>(API_ENDPOINTS.CLAIMS);
        if (mounted) setClaims(cls || []);
      } catch (err) {
        if (mounted) setError(err instanceof Error ? err.message : String(err));
      } finally {
        if (mounted) setLoading(false);
      }
    }

    load();
    return () => { mounted = false; };
  }, [user]);

  if (loading) return <LoadingState message="Loading dashboard..." />;
  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />;

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Welcome{user?.fullName ? `, ${user.fullName}` : ''}</h1>
          <p className="text-[var(--color-text-secondary)]">Your claims and policies at a glance.</p>
        </div>
        <div className="flex items-center gap-3">
          <Link to="/policies" className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg">View Policies</Link>
          <Link to="/claims/new" className="px-4 py-2 border border-[var(--color-border)] rounded-lg">File a Claim</Link>
        </div>
      </header>

      <section aria-labelledby="policies-heading">
        <h2 id="policies-heading" className="text-lg font-semibold">Active Policies</h2>
        {policies && policies.length > 0 ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
            {policies.map(p => (
              <div key={p.id} className="bg-[var(--color-background)] border border-[var(--color-border)] p-4 rounded-lg">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="font-medium">{p.policyNumber}</h3>
                    <p className="text-sm text-[var(--color-text-secondary)]">{p.productType} • {p.coveragePlanName}</p>
                  </div>
                  <div className="text-sm font-semibold">{p.status}</div>
                </div>
                <div className="mt-3 text-sm text-[var(--color-text-secondary)]">
                  <div>Effective: {p.effectiveDate}</div>
                  <div>Renewal: {p.renewalDate ?? '—'}</div>
                </div>
                <div className="mt-4">
                  <Link to={`/policies/${p.id}`} className="text-[var(--color-primary)]">View details</Link>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState
            title="No active policies"
            description="You currently have no active policies. Explore products or create a policy."
            action={<Link to="/products" className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg">Explore Products</Link>}
          />
        )}
      </section>

      <section aria-labelledby="claims-heading">
        <h2 id="claims-heading" className="text-lg font-semibold">Recent Claims</h2>
        {claims && claims.length > 0 ? (
          <div className="mt-4 space-y-3">
            {claims.slice(0, 5).map(c => (
              <div key={c.id} className="bg-[var(--color-background)] border border-[var(--color-border)] p-3 rounded-lg flex items-center justify-between">
                <div>
                  <div className="font-medium">{c.claimNumber} — {c.incidentType}</div>
                  <div className="text-sm text-[var(--color-text-secondary)]">{new Date(c.incidentDate).toLocaleDateString()} • Status: <span className="font-semibold">{c.status}</span></div>
                </div>
                <div>
                  <Link to={`/claims/${c.id}`} className="text-[var(--color-primary)]">View</Link>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState
            title="No recent claims"
            description="You have not filed any claims yet."
            action={<Link to="/claims/new" className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg">File a Claim</Link>}
          />
        )}
      </section>

      <section aria-labelledby="account-heading">
        <h2 id="account-heading" className="text-lg font-semibold">Account</h2>
        {customer ? (
          <div className="mt-4 bg-[var(--color-background)] border border-[var(--color-border)] p-4 rounded-lg">
            <div className="font-medium">{customer.fullName}</div>
            <div className="text-sm text-[var(--color-text-secondary)]">Username: {customer.username}</div>
            <div className="mt-2">
              <Link to="/profile" className="text-[var(--color-primary)]">Manage profile</Link>
            </div>
          </div>
        ) : (
          <EmptyState title="No account info" description="Unable to load account details." />
        )}
      </section>
    </div>
  );
};

export default DashboardPage;
