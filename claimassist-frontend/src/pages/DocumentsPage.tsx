import React from 'react';
void React;
import { useAuth } from '../contexts/AuthContext';
import { EmptyState } from '../components/ui/EmptyState';

export default function DocumentsPage(): React.ReactElement {
  const _user = useAuth();
  void _user;

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">Documents</h1>

      <EmptyState
        title="Document management not available"
        description={
          'The current backend exposes only per-claim document metadata via an internal endpoint. There is no customer-level document listing, upload, or download endpoint available to the browser client.'
        }
        action={<div className="mt-3"><a href="/claims" className="text-blue-600 underline">View Claims</a></div>}
      />
    </div>
  );
}
