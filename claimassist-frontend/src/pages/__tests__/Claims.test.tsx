/// <reference types="vitest" />
import '@testing-library/jest-dom';
import React from 'react';
void React;
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../../contexts/AuthContext';
import { apiClient } from '../../services/api-client';
import ClaimsListPage from '../../pages/ClaimsListPage';
import ClaimDetailsPage from '../../pages/ClaimDetailsPage';
import type { ClaimSummaryResponse } from '../../types';

const sampleClaims: ClaimSummaryResponse[] = [
  {
    id: 11,
    claimNumber: 'CLM-001',
    policyId: 1,
    incidentType: 'Accident',
    status: 'SUBMITTED',
    estimatedAmountCents: 250000,
    approvedAmountCents: undefined,
    role: 'POLICYHOLDER',
    incidentDate: '2025-02-01T00:00:00Z',
    createdAt: '2025-02-02T00:00:00Z',
  },
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
    await waitFor(() => expect(screen.getByText(/CLM-001/i)).toBeInTheDocument());
    expect(screen.getByText(/View Claim/i)).toBeInTheDocument();
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

  it('renders claim details, status, and refresh action', async () => {
    vi.spyOn(apiClient, 'get').mockImplementation(async (endpoint: string) => {
      if (endpoint === '/api/v1/claims/11') return sampleClaims[0] as any;
      return [] as any;
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/claims/11']}>
          <Routes>
            <Route path="/claims/:id" element={<ClaimDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Loading claim.../i)).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('heading', { name: /CLM-001/i })).toBeInTheDocument());
    expect(screen.getByText(/Current status/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Refresh Status/i })).toBeInTheDocument();
    expect(screen.getByText(/Claim Number/i)).toBeInTheDocument();
    expect(screen.getByText(/Back to claims/i)).toBeInTheDocument();
  });

  it('shows claim not found when detail API returns 404', async () => {
    vi.spyOn(apiClient, 'get').mockImplementation(async () => {
      throw new Error('404 Not Found');
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/claims/999']}>
          <Routes>
            <Route path="/claims/:id" element={<ClaimDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Claim not found/i)).toBeInTheDocument());
  });

  it('refreshes claim status when the refresh button is clicked', async () => {
    const getSpy = vi.spyOn(apiClient, 'get');
    getSpy.mockResolvedValue(sampleClaims[0] as any);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/claims/11']}>
          <Routes>
            <Route path="/claims/:id" element={<ClaimDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByRole('button', { name: /Refresh Status/i })).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /Refresh Status/i }));
    await waitFor(() => expect(getSpy).toHaveBeenCalled());
  });
});
