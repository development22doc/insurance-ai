import React, { useEffect, useState } from 'react';
void React;
import { useAuth } from '../contexts/AuthContext';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import { LoadingState } from '../components/ui/LoadingState';
import { EmptyState } from '../components/ui/EmptyState';
import { ErrorState } from '../components/ui/ErrorState';

interface CustomerResponse {
  id: number;
  username?: string;
  fullName?: string;
  kycStatus?: string;
}

export default function ProfilePage(): React.ReactElement {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [customer, setCustomer] = useState<CustomerResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const [fullName, setFullName] = useState('');
  const [readonlyFallback, setReadonlyFallback] = useState(false);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError(null);

      const customerId = user?.customerId;
      if (!customerId) {
        setError('No customer ID available for the current session.');
        setLoading(false);
        return;
      }

      try {
        // Try to GET the customer record from backend
        const resp = await apiClient.get<CustomerResponse>(API_ENDPOINTS.CUSTOMER_UPDATE(customerId));
        setCustomer(resp);
        setFullName(resp.fullName || '');
      } catch (err) {
        // If GET isn't available, fall back to stored auth_user (read-only)
        const stored = sessionStorage.getItem('auth_user');
        if (stored) {
          try {
            const parsed = JSON.parse(stored);
            setCustomer({ id: parsed.customerId, fullName: parsed.fullName, username: parsed.username });
            setReadonlyFallback(true);
          } catch {
            setError('Failed to parse local profile data');
          }
        } else {
          setError('Unable to load profile.');
        }
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [user]);

  const onSave = async () => {
    if (!customer) return;
    setLoading(true);
    setError(null);
    try {
      const updated = await apiClient.patch<CustomerResponse>(API_ENDPOINTS.CUSTOMER_UPDATE(customer.id), {
        fullName,
      });
      setCustomer(updated);
      sessionStorage.setItem('auth_user', JSON.stringify({ ...JSON.parse(sessionStorage.getItem('auth_user') || '{}'), fullName: updated.fullName, customerId: updated.id }));
      setEditing(false);
    } catch (err: any) {
      setError(err?.message || 'Failed to save profile');
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <LoadingState message="Loading profile..." />;
  if (error) return <ErrorState message={error} />;
  if (!customer) return <EmptyState title="No profile" description="No profile data available." />;

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">Profile</h1>

      {readonlyFallback && (
        <div className="mb-4 p-3 bg-yellow-50 border-l-4 border-yellow-300 text-sm">
          Profile read-only: backend does not expose a customer GET endpoint. Showing cached session data.
        </div>
      )}

      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700">Customer ID</label>
        <div className="mt-1 text-sm">{customer.id}</div>
      </div>

      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700">Username</label>
        <div className="mt-1 text-sm">{customer.username ?? '—'}</div>
      </div>

      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700">Full name</label>
        {!editing ? (
          <div className="mt-1 text-sm">{customer.fullName ?? '—'}</div>
        ) : (
          <input
            value={fullName}
            onChange={e => setFullName(e.target.value)}
            className="mt-1 block w-full rounded border p-2"
            aria-label="Full name"
          />
        )}
      </div>

      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700">KYC Status</label>
        <div className="mt-1 text-sm">{customer.kycStatus ?? 'Unknown'}</div>
      </div>

      {!readonlyFallback && (
        <div className="flex gap-2">
          {!editing ? (
            <button className="btn btn-primary" onClick={() => setEditing(true)}>Edit</button>
          ) : (
            <>
              <button className="btn btn-primary" onClick={onSave} disabled={loading}>Save</button>
              <button className="btn" onClick={() => { setEditing(false); setFullName(customer.fullName || ''); }}>Cancel</button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
