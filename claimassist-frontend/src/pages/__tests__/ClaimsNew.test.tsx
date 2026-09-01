/// <reference types="vitest" />
import '@testing-library/jest-dom';
import React from 'react';
void React;
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../../contexts/AuthContext';
import { apiClient } from '../../services/api-client';
import ClaimsNewPage from '../ClaimsNewPage';

const mockPolicies = [
  { id: 1, policyNumber: 'POL-123', product: 'Auto', status: 'ACTIVE' },
  { id: 2, policyNumber: 'POL-456', product: 'Home', status: 'ACTIVE' },
];

describe('Claims wizard', () => {
  beforeEach(() => {
    sessionStorage.setItem('auth_user', JSON.stringify({ customerId: 123, fullName: 'Alice Example', roles: ['CUSTOMER'] }));
    sessionStorage.setItem('access_token', 'dummy.header.payload');
    sessionStorage.setItem('token_expiry', (Date.now() + 60 * 60 * 1000).toString());
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('loads policies and navigates steps', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue(mockPolicies as any);

    render(
      <AuthProvider>
        <MemoryRouter>
          <ClaimsNewPage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Loading policies/i)).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText(/Select Policy/i)).toBeInTheDocument());

    // Select first policy
    fireEvent.click(screen.getByLabelText(/POL-123/i) || screen.getAllByRole('radio')[0]);

    // Next to incident
    fireEvent.click(screen.getByText(/Next/i));

    await waitFor(() => expect(screen.getByText(/Incident Details/i)).toBeInTheDocument());
  });

  it('validates and submits claim', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue(mockPolicies as any);
    vi.spyOn(apiClient, 'post').mockResolvedValue({ id: 99, claimNumber: 'CL-099', status: 'SUBMITTED', incidentType: 'Accident' } as any);

    render(
      <AuthProvider>
        <MemoryRouter>
          <ClaimsNewPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Select Policy/i)).toBeInTheDocument());

    // choose first policy
    const firstRadio = screen.getAllByRole('radio')[0];
    fireEvent.click(firstRadio);

    fireEvent.click(screen.getByText(/Next/i));

    await waitFor(() => expect(screen.getByText(/Incident Details/i)).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/Incident type/i), { target: { value: 'Accident' } });
    fireEvent.change(screen.getByLabelText(/Incident date/i), { target: { value: new Date().toISOString().slice(0,10) } });

    fireEvent.click(screen.getByText(/Next/i));

    await waitFor(() => expect(screen.getByText(/Review/i)).toBeInTheDocument());

    fireEvent.click(screen.getByText(/Submit Claim/i));

    await waitFor(() => expect(apiClient.post).toHaveBeenCalled());
  });
});
