/// <reference types="vitest" />
import '@testing-library/jest-dom';
import React from 'react';
void React;
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../../contexts/AuthContext';
import { apiClient } from '../../services/api-client';
import ClaimsListPage from '../../pages/ClaimsListPage';
import ClaimDetailsPage from '../../pages/ClaimDetailsPage';
import type { ClaimSummaryResponse } from '../../types';

const sampleClaims: ClaimSummaryResponse[] = [
  { id: 11, claimNumber: 'CL-001', policyId: 1, incidentType: 'Accident', status: 'OPEN', estimatedAmountCents: undefined as any, approvedAmountCents: undefined as any, role: 'POLICYHOLDER', incidentDate: '2025-02-01' as any, createdAt: '2025-02-02' as any },
];

describe('Claims pages', () => {
  beforeEach(() => {
    sessionStorage.setItem('auth_user', JSON.stringify({ customerId: 123, fullName: 'Alice Example', roles: ['CUSTOMER'] }));
    sessionStorage.setItem('access_token', 'dummy.header.payload');
    sessionStorage.setItem('token_expiry', (Date.now() + 60 * 60 * 1000).toString());
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('renders claims list after loading', async () => {
    vi.spyOn(apiClient, 'get').mockImplementation(async (endpoint: string) => {
      if (endpoint === '/api/v1/claims') return sampleClaims as any;
      return [] as any;
    });

    render(
      <AuthProvider>
        <MemoryRouter>
          <ClaimsListPage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Loading claims.../i)).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText(/CL-001/i)).toBeInTheDocument());
  });

  it('shows empty state when no claims', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue([] as any);

    render(
      <AuthProvider>
        <MemoryRouter>
          <ClaimsListPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      const matches = screen.getAllByText(/No claims/i);
      expect(matches.length).toBeGreaterThan(0);
    });
  });

  it('renders claim details and back link', async () => {
    vi.spyOn(apiClient, 'get').mockImplementation(async (endpoint: string) => {
      if (endpoint === '/api/v1/claims/11') return sampleClaims[0] as any;
      return [] as any;
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/claims/11"]}>
          <Routes>
            <Route path="/claims/:id" element={<ClaimDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Loading claim.../i)).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('heading', { name: /CL-001/i })).toBeInTheDocument());

    expect(screen.getByText(/Overview/i)).toBeInTheDocument();
    expect(screen.getByText(/Back to claims/i)).toBeInTheDocument();
  });

  it('shows claim not found when detail API returns 404', async () => {
    vi.spyOn(apiClient, 'get').mockImplementation(async (_endpoint: string) => {
      const err: any = new Error('404 Not Found');
      throw err;
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/claims/999"]}>
          <Routes>
            <Route path="/claims/:id" element={<ClaimDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Claim not found/i)).toBeInTheDocument());
  });
});
