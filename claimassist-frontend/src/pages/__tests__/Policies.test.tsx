/// <reference types="vitest" />
import '@testing-library/jest-dom';
import React from 'react';
void React;
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../../contexts/AuthContext';
import { apiClient } from '../../services/api-client';
import PoliciesListPage from '../../pages/PoliciesListPage';
import PolicyDetailsPage from '../../pages/PolicyDetailsPage';
import type { PolicyResponse } from '../../types';

const samplePolicies: PolicyResponse[] = [
  { id: 1, policyNumber: 'POL-1', status: 'ACTIVE', coveragePlanName: 'Plan A', productType: 'Vehicle', effectiveDate: '2025-01-01', renewalDate: '2026-01-01' },
  { id: 2, policyNumber: 'POL-2', status: 'LAPSED', coveragePlanName: 'Plan B', productType: 'Home', effectiveDate: '2023-05-01', renewalDate: '' },
];

describe('Policies pages', () => {
  beforeEach(() => {
    sessionStorage.setItem('auth_user', JSON.stringify({ customerId: 123, fullName: 'Alice Example', roles: ['CUSTOMER'] }));
    sessionStorage.setItem('access_token', 'dummy.header.payload');
    sessionStorage.setItem('token_expiry', (Date.now() + 60 * 60 * 1000).toString());
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('renders policies list after loading', async () => {
    vi.spyOn(apiClient, 'get').mockImplementation(async (endpoint: string) => {
      if (endpoint === '/api/v1/policies/all') return samplePolicies as any;
      return [] as any;
    });

    render(
      <AuthProvider>
        <MemoryRouter>
          <PoliciesListPage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Loading policies.../i)).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText('POL-1')).toBeInTheDocument());

    expect(screen.getByText('POL-2')).toBeInTheDocument();
  });

  it('shows empty state when no policies', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue([] as any);

    render(
      <AuthProvider>
        <MemoryRouter>
          <PoliciesListPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/No policies found/i)).toBeInTheDocument();
    });
  });

  it('renders policy details and back link', async () => {
    vi.spyOn(apiClient, 'get').mockImplementation(async (endpoint: string) => {
      if (endpoint === '/api/v1/policies/1') return samplePolicies[0] as any;
      return [] as any;
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/policies/1"]}>
          <Routes>
            <Route path="/policies/:id" element={<PolicyDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Loading policy.../i)).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText(/Policy POL-1/i)).toBeInTheDocument());

    expect(screen.getByText(/Overview/i)).toBeInTheDocument();
    expect(screen.getByText(/Dates/i)).toBeInTheDocument();
    expect(screen.getByText(/Back to policies/i)).toBeInTheDocument();
  });

  it('shows not found when detail API returns 404', async () => {
    vi.spyOn(apiClient, 'get').mockImplementation(async (_endpoint: string) => {
      const err: any = new Error('404 Not Found');
      throw err;
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/policies/999"]}>
          <Routes>
            <Route path="/policies/:id" element={<PolicyDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Policy not found/i)).toBeInTheDocument());
  });
});
