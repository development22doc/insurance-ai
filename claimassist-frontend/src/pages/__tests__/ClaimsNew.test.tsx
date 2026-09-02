/// <reference types="vitest" />
import '@testing-library/jest-dom';
import React from 'react';
void React;
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../../contexts/AuthContext';
import { apiClient } from '../../services/api-client';
import ClaimsNewPage from '../ClaimsNewPage';

const mockPolicies = [
  { id: 1, policyNumber: 'POL-123', productType: 'Auto', coveragePlanName: 'Basic Auto', status: 'ACTIVE' },
  { id: 2, policyNumber: 'POL-456', productType: 'Home', coveragePlanName: 'Home Shield', status: 'ACTIVE' },
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

    fireEvent.click(screen.getAllByRole('radio')[0]);
    fireEvent.click(screen.getByText(/Next/i));

    await waitFor(() => expect(screen.getByText(/Incident Details/i)).toBeInTheDocument());
  });

  it('uses a single idempotency key for a submission and shows success state', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({
      id: 99,
      claimNumber: 'CLM-099',
      status: 'SUBMITTED',
      incidentType: 'Accident',
    } as any);
    vi.spyOn(apiClient, 'get').mockResolvedValue(mockPolicies as any);

    render(
      <AuthProvider>
        <MemoryRouter>
          <ClaimsNewPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Select Policy/i)).toBeInTheDocument());
    fireEvent.click(screen.getAllByRole('radio')[0]);
    fireEvent.click(screen.getByText(/Next/i));
    fireEvent.change(screen.getByLabelText(/Incident type/i), { target: { value: 'Accident' } });
    fireEvent.change(screen.getByLabelText(/Incident date/i), { target: { value: new Date().toISOString().slice(0, 10) } });
    fireEvent.click(screen.getByText(/Next/i));
    fireEvent.click(screen.getByRole('button', { name: /Submit Claim/i }));

    await waitFor(() => expect(postSpy).toHaveBeenCalledTimes(1));
    const [, , options] = postSpy.mock.calls[0];
    expect((options as { headers: Record<string, string> } | undefined)?.headers['Idempotency-Key']).toBe(
      sessionStorage.getItem('claim_idempotency_key')
    );

    expect(screen.getByText(/Claim Submitted Successfully/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Track Claim/i })).toHaveAttribute('href', '/claims/99');
  });

  it('disables the submit button while the request is in flight', async () => {
    let resolveRequest: (value: unknown) => void = () => undefined;
    vi.spyOn(apiClient, 'get').mockResolvedValue(mockPolicies as any);
    vi.spyOn(apiClient, 'post').mockImplementation(
      () => new Promise((resolve) => {
        resolveRequest = resolve;
      })
    );

    render(
      <AuthProvider>
        <MemoryRouter>
          <ClaimsNewPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Select Policy/i)).toBeInTheDocument());
    fireEvent.click(screen.getAllByRole('radio')[0]);
    fireEvent.click(screen.getByText(/Next/i));
    fireEvent.change(screen.getByLabelText(/Incident type/i), { target: { value: 'Accident' } });
    fireEvent.change(screen.getByLabelText(/Incident date/i), { target: { value: new Date().toISOString().slice(0, 10) } });
    fireEvent.click(screen.getByText(/Next/i));
    const submitButton = screen.getByRole('button', { name: /Submit Claim/i });

    fireEvent.click(submitButton);
    await waitFor(() => expect(submitButton).toBeDisabled());

    resolveRequest({
      id: 100,
      claimNumber: 'CLM-100',
      status: 'SUBMITTED',
      incidentType: 'Accident',
    });
    await waitFor(() => expect(screen.getByText(/Claim Submitted Successfully/i)).toBeInTheDocument());
  });
});
