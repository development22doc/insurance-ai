/// <reference types="vitest" />
import '@testing-library/jest-dom';
import React from 'react';
void React;
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../../contexts/AuthContext';
import { apiClient } from '../../services/api-client';
import ProfilePage from '../ProfilePage';

const mockCustomer = { id: 123, username: 'alice', fullName: 'Alice Example', kycStatus: 'VERIFIED' };

describe('Profile page', () => {
  beforeEach(() => {
    sessionStorage.setItem('auth_user', JSON.stringify({ customerId: 123, fullName: 'Alice Example', roles: ['CUSTOMER'] }));
    sessionStorage.setItem('access_token', 'dummy.header.payload');
    sessionStorage.setItem('token_expiry', (Date.now() + 60 * 60 * 1000).toString());
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('loads and displays profile from backend', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue(mockCustomer as any);

    render(
      <AuthProvider>
        <MemoryRouter>
          <ProfilePage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Loading profile/i)).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText(/Alice Example/i)).toBeInTheDocument());
    expect(screen.getByText(/^alice$/i)).toBeInTheDocument();
    expect(screen.getByText(/VERIFIED/i)).toBeInTheDocument();
  });

  it('allows editing and saves via PATCH', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue(mockCustomer as any);
    vi.spyOn(apiClient, 'patch').mockImplementation(async (_endpoint: string, data: any) => ({ ...mockCustomer, fullName: data.fullName }));

    render(
      <AuthProvider>
        <MemoryRouter>
          <ProfilePage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Alice Example/i)).toBeInTheDocument());

    fireEvent.click(screen.getByText(/Edit/i));

    const input = screen.getByLabelText(/Full name/i) as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'Alice B' } });

    fireEvent.click(screen.getByText(/Save/i));

    await waitFor(() => expect(screen.getByText(/Alice B/i)).toBeInTheDocument());
  });

  it('falls back to readonly when GET fails', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue(new Error('404'));

    render(
      <AuthProvider>
        <MemoryRouter>
          <ProfilePage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Profile read-only/i)).toBeInTheDocument());
    expect(screen.getByText(/Alice Example/i)).toBeInTheDocument();
    // Edit button should not be present in read-only mode
    expect(screen.queryByText(/Edit/i)).not.toBeInTheDocument();
  });
});
