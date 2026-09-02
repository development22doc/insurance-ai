/// <reference types="vitest" />
import '@testing-library/jest-dom';
import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../../contexts/AuthContext';
import { AdminUsersPage } from '../AdminUsersPage';
import { AdminDashboardPage } from '../AdminDashboardPage';

describe('Admin preview pages', () => {
  beforeEach(() => {
    sessionStorage.setItem('auth_user', JSON.stringify({ customerId: 0, fullName: 'Admin', roles: ['ADMIN'] }));
    sessionStorage.setItem('access_token', 'dummy.header.payload');
    sessionStorage.setItem('token_expiry', (Date.now() + 60 * 60 * 1000).toString());
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('renders admin dashboard preview', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <AdminDashboardPage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Admin Dashboard/i)).toBeInTheDocument();
    expect(screen.getByText(/Preview-only admin console/i)).toBeInTheDocument();
  });

  it('renders users page with disabled actions', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <AdminUsersPage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/User Management is coming soon/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Create user/i })).toBeDisabled();
  });
});
