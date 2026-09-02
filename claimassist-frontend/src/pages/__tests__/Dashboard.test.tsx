/// <reference types="vitest" />
import React from 'react';
import '@testing-library/jest-dom';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
void React;
import { AuthProvider } from '../../contexts/AuthContext';
import { DashboardPage } from '../../pages/DashboardPage';
import { apiClient } from '../../services/api-client';
import { vi } from 'vitest';
import type { PolicyResponse, ClaimSummaryResponse, CustomerResponse } from '../../types';

describe('Dashboard page', () => {
  const mockPolicies: PolicyResponse[] = [
    { id: 1, policyNumber: 'POL-123', status: 'ACTIVE', coveragePlanName: 'Standard', productType: 'Vehicle', effectiveDate: '2025-01-01', renewalDate: '2026-01-01' },
  ];

  const mockClaims: ClaimSummaryResponse[] = [
    { id: 11, claimNumber: 'CL-001', policyId: 1, incidentType: 'Accident', status: 'OPEN', incidentDate: '2025-02-01', createdAt: '2025-02-02', role: 'POLICYHOLDER' },
  ];

  const mockCustomer: CustomerResponse = { id: 123, username: 'alice', fullName: 'Alice Example', kycStatus: 'VERIFIED' };

  beforeEach(() => {
    // Ensure auth_user and tokens exist in sessionStorage so AuthProvider treats user as authenticated
    sessionStorage.setItem('auth_user', JSON.stringify({ customerId: 123, fullName: 'Alice Example', roles: ['CUSTOMER'] }));
    sessionStorage.setItem('access_token', 'dummy.header.payload');
    // set token expiry in the future
    sessionStorage.setItem('token_expiry', (Date.now() + 60 * 60 * 1000).toString());
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('renders dashboard with policies and claims', async () => {
    vi.spyOn(apiClient, 'get').mockImplementation(async (endpoint: string) => {
      if (endpoint === '/api/v1/policies/all') return mockPolicies as any;
      if (endpoint === '/api/v1/claims') return mockClaims as any;
      if (endpoint === '/api/v1/customers/123') return mockCustomer as any;
      return [] as any;
    });

    render(
      <AuthProvider>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Loading dashboard.../i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/Active Policies/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/POL-123/)).toBeInTheDocument();
    expect(screen.getByText(/CL-001/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Welcome\s*,\s*Alice Example/ })).toBeInTheDocument();
  });

  it('shows empty states when no data', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue([] as any);
    sessionStorage.setItem('auth_user', JSON.stringify({ customerId: 123, fullName: 'Alice Example', roles: ['CUSTOMER'] }));

    render(
      <AuthProvider>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      // At least one empty state heading should be present (allow multiple matches)
      const noActive = screen.queryAllByText(/No active policies/i);
      const noRecent = screen.queryAllByText(/No recent claims/i);
      expect(noActive.length + noRecent.length).toBeGreaterThan(0);
    });
  });
});
