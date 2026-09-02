import React from 'react';
import { UpcomingFeature } from '../components/UpcomingFeature';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';

export const AdminDocumentsPage: React.FC = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold">Document Management</h1>
      <p className="text-[var(--color-text-secondary)] mb-4">Document management preview. Upload/download actions are disabled until browser-facing APIs are implemented.</p>

      <Card className="p-4">
        <div className="text-sm text-[var(--color-text-secondary)]">No documents available in preview.</div>
        <div className="mt-4">
          <Button disabled>Upload</Button>
          <Button disabled className="ml-2">Download</Button>
        </div>
      </Card>

      <div className="mt-6">
        <UpcomingFeature title="Document Management" description="Manage claim-related documents. Actions disabled in preview." />
      </div>
    </div>
  );
};
