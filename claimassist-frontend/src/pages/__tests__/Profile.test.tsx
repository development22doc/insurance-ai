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

  it('renders profile from authenticated session and displays basic fields', async () => {
    // No backend GET; data should come from session/auth
    render(
      <AuthProvider>
        <MemoryRouter>
          <ProfilePage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Alice Example/i)).toBeInTheDocument());
    // Username is not part of auth session by default — show placeholder
    expect(screen.getByText(/^—$/)).toBeInTheDocument();
    expect(screen.getByText(/Unknown/i)).toBeInTheDocument();
  });

  it('allows editing and saves via PATCH with only fullName', async () => {
    // Mock PATCH response
    vi.spyOn(apiClient, 'patch').mockImplementation(async (_endpoint: string, data: any) => ({ id: 123, username: 'alice', fullName: data.fullName, kycStatus: 'VERIFIED' } as any));

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
    // Ensure sessionStorage updated
    const stored = JSON.parse(sessionStorage.getItem('auth_user') || '{}');
    expect(stored.fullName).toBe('Alice B');
  });

  it('shows error when no session customerId available', async () => {
    // Clear session to simulate missing auth
    sessionStorage.clear();

    render(
      <AuthProvider>
        <MemoryRouter>
          <ProfilePage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/No customer ID available/i)).toBeInTheDocument());
  });
});
