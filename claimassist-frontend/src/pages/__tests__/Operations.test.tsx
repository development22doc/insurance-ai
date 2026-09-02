/// <reference types="vitest" />
import '@testing-library/jest-dom';
import React from 'react';
void React;
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import { ProtectedRoute } from '../../components/auth';
import { AuthProvider } from '../../contexts/AuthContext';
import { apiClient } from '../../services/api-client';
import { OperationsClaimsPage } from '../../pages/OperationsClaimsPage';
import { OperationsDashboardPage } from '../../pages/OperationsDashboardPage';
import { OperationsClaimWorkspacePage } from '../../pages/OperationsClaimWorkspacePage';
import type { ClaimSummaryResponse } from '../../types';

const sampleClaims: ClaimSummaryResponse[] = [
  {
    id: 101,
    claimNumber: 'CLM-OPS-001',
    policyId: 12,
    incidentType: 'Accident',
    status: 'UNDER_REVIEW',
    estimatedAmountCents: 345000,
    approvedAmountCents: undefined,
    role: 'ADJUSTER',
    incidentDate: '2025-02-03T00:00:00Z',
    createdAt: '2025-02-04T00:00:00Z',
  },
  {
    id: 102,
    claimNumber: 'CLM-OPS-002',
    policyId: 22,
    incidentType: 'Theft',
    status: 'SUBMITTED',
    estimatedAmountCents: 150000,
    approvedAmountCents: undefined,
    role: 'ADJUSTER',
    incidentDate: '2025-02-05T00:00:00Z',
    createdAt: '2025-02-06T00:00:00Z',
  },
];

const makeJwt = (roles: string[]) => {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({ realm_access: { roles }, userId: 'user-1' }));
  return `${header}.${payload}.signature`;
};

const setAuth = (roles: string[]) => {
  sessionStorage.setItem('access_token', makeJwt(roles));
  sessionStorage.setItem('token_expiry', (Date.now() + 60 * 60 * 1000).toString());
  sessionStorage.setItem(
    'auth_user',
    JSON.stringify({ customerId: 1, fullName: 'Ops User', roles, userId: 'user-1' })
  );
};

describe('Operations pages', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('renders the operations dashboard for an authorized role', async () => {
    setAuth(['ADJUSTER']);
    vi.spyOn(apiClient, 'get').mockResolvedValue(sampleClaims as any);

    render(
      <AuthProvider>
        <MemoryRouter>
          <OperationsDashboardPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByRole('heading', { name: /Dashboard/i })).toBeInTheDocument());
    expect(screen.getByText(/Visible claims/i)).toBeInTheDocument();
    expect(screen.getByText(/CLM-OPS-001/i)).toBeInTheDocument();
  });

  it('renders queue content and status labels', async () => {
    setAuth(['AUDITOR']);
    vi.spyOn(apiClient, 'get').mockResolvedValue(sampleClaims as any);

    render(
      <AuthProvider>
        <MemoryRouter>
          <OperationsClaimsPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByRole('heading', { name: /Claims queue/i })).toBeInTheDocument());
    expect(screen.getAllByText(/CLM-OPS-001/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/UNDER_REVIEW/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/View Claim/i).length).toBeGreaterThan(0);
  });

  it('renders a read-only workspace for auditors and hides status mutators', async () => {
    setAuth(['AUDITOR']);
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      ...sampleClaims[0],
      status: 'UNDER_REVIEW',
    } as any);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/operations/claims/101']}>
          <Routes>
            <Route path="/operations/claims/:id" element={<OperationsClaimWorkspacePage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByRole('heading', { name: /CLM-OPS-001/i })).toBeInTheDocument());
    expect(screen.getByText(/Adjusters are the role authorized to update claim status/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Approve claim/i })).not.toBeInTheDocument();
  });

  it('renders a mutable workspace for adjusters and submits a real backend transition', async () => {
    setAuth(['ADJUSTER']);
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      ...sampleClaims[0],
      status: 'SUBMITTED',
    } as any);
    const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValue({
      ...sampleClaims[0],
      status: 'UNDER_REVIEW',
    } as any);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/operations/claims/101']}>
          <Routes>
            <Route path="/operations/claims/:id" element={<OperationsClaimWorkspacePage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByRole('button', { name: /Move to Under Review/i })).toBeInTheDocument());
    screen.getByRole('button', { name: /Move to Under Review/i }).click();
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    screen.getByRole('button', { name: /^Confirm$/i }).click();

    await waitFor(() => expect(patchSpy).toHaveBeenCalledWith('/api/v1/claims/101/status', {
      status: 'UNDER_REVIEW',
      note: undefined,
    }));
  });

  it('shows empty state when the queue is empty', async () => {
    setAuth(['ADJUSTER']);
    vi.spyOn(apiClient, 'get').mockResolvedValue([] as any);

    render(
      <AuthProvider>
        <MemoryRouter>
          <OperationsClaimsPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Queue is empty/i)).toBeInTheDocument());
  });

  it('shows an API error state when the dashboard request fails', async () => {
    setAuth(['ADJUSTER']);
    vi.spyOn(apiClient, 'get').mockRejectedValue(new Error('The queue is unavailable'));

    render(
      <AuthProvider>
        <MemoryRouter>
          <OperationsDashboardPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/The queue is unavailable/i)).toBeInTheDocument());
  });

  it('redirects unauthorized roles to the unauthorized page', async () => {
    setAuth(['CUSTOMER']);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/operations']}>
          <Routes>
            <Route
              path="/operations"
              element={
                <ProtectedRoute requiredRoles={['ADJUSTER', 'AUDITOR']}>
                  <div>Operations content</div>
                </ProtectedRoute>
              }
            />
            <Route path="/unauthorized" element={<div>Unauthorized page</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Unauthorized page/i)).toBeInTheDocument());
  });

  it('shows a 404 view when the workspace claim is missing', async () => {
    setAuth(['ADJUSTER']);
    vi.spyOn(apiClient, 'get').mockRejectedValue(new Error('404 Not Found'));

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/operations/claims/999']}>
          <Routes>
            <Route path="/operations/claims/:id" element={<OperationsClaimWorkspacePage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Claim not found/i)).toBeInTheDocument());
  });
});
