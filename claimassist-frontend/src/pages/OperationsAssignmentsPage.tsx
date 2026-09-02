import React from 'react';
import { UpcomingFeature } from '../components/UpcomingFeature';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';

export const OperationsAssignmentsPage: React.FC = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold">Assignments</h1>
      <p className="text-[var(--color-text-secondary)] mb-4">Assignment workflows preview. Assign/reassign actions are disabled until backend support exists.</p>

      <Card className="p-4">
        <div className="text-sm text-[var(--color-text-secondary)]">No assignments in preview.</div>
        <div className="mt-4">
          <Button disabled>Assign</Button>
        </div>
      </Card>

      <div className="mt-6">
        <UpcomingFeature title="Assignment Management" description="Assign adjusters or surveyors to claims. Actions disabled in preview." />
      </div>
    </div>
  );
};
