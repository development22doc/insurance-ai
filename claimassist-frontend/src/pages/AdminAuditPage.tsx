import React from 'react';
import { UpcomingFeature } from '../components/UpcomingFeature';
import { Card } from '../components/ui/Card';

export const AdminAuditPage: React.FC = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold">Audit Management</h1>
      <p className="text-[var(--color-text-secondary)] mb-4">Audit APIs are not yet available. This page is a preview-only UI for future audit search and export capabilities.</p>

      <Card className="p-4">
        <div className="text-sm text-[var(--color-text-secondary)]">No audit events are currently available. Audit APIs are coming soon.</div>
      </Card>

      <div className="mt-6">
        <UpcomingFeature title="Audit Management" description="Search and export audit events. Sample previews can be shown when backend is ready." />
      </div>
    </div>
  );
};
