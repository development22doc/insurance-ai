import React from 'react';
import { useAuth } from '../contexts/AuthContext';
import { ClaimsPublicPage } from './ClaimsPublicPage';
import { ClaimsListPage } from './ClaimsListPage';

/**
 * ClaimsPage handles both public and authenticated claims views.
 * - Unauthenticated users see the public ClaimsPublicPage (informational)
 * - Authenticated users see their actual ClaimsListPage
 */
export const ClaimsPage: React.FC = () => {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <ClaimsListPage />;
  }

  return <ClaimsPublicPage />;
};

export default ClaimsPage;
