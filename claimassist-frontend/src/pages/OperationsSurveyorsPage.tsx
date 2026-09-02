import React from 'react';
import { UpcomingFeature } from '../components/UpcomingFeature';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';

export const OperationsSurveyorsPage: React.FC = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold">Surveyors</h1>
      <p className="text-[var(--color-text-secondary)] mb-4">Surveyor management preview. Scheduling and reports are disabled until backend APIs are ready.</p>

      <Card className="p-4">
        <div className="text-sm text-[var(--color-text-secondary)]">No surveyor data in preview.</div>
        <div className="mt-4">
          <Button disabled>Add surveyor</Button>
        </div>
      </Card>

      <div className="mt-6">
        <UpcomingFeature title="Surveyor Management" description="Manage surveyors and scheduling. Actions disabled in preview." />
      </div>
    </div>
  );
};
