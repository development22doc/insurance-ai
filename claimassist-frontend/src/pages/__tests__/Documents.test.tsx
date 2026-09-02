/// <reference types="vitest" />
import '@testing-library/jest-dom';
import React from 'react';
void React;
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../../contexts/AuthContext';
import DocumentsPage from '../DocumentsPage';

describe('Documents page', () => {
  beforeEach(() => {
    sessionStorage.setItem('auth_user', JSON.stringify({ customerId: 123, fullName: 'Alice Example', roles: ['CUSTOMER'] }));
    sessionStorage.setItem('access_token', 'dummy.header.payload');
    sessionStorage.setItem('token_expiry', (Date.now() + 60 * 60 * 1000).toString());
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('shows informational empty state when documents are not exposed', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <DocumentsPage />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Document management not available/i)).toBeInTheDocument();
    expect(screen.getByText(/View Claims/i)).toBeInTheDocument();
  });
});
