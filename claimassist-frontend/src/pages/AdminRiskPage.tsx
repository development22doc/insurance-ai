import React from 'react';
import { UpcomingFeature } from '../components/UpcomingFeature';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';

export const AdminRiskPage: React.FC = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold">Fraud & Risk Management</h1>
      <p className="text-[var(--color-text-secondary)] mb-4">Rule-based risk configuration preview. Rules and thresholds are not editable in preview mode.</p>

      <div className="mb-4 flex justify-between items-center">
        <div className="text-sm text-[var(--color-text-secondary)]">No rules available in preview</div>
        <div>
          <Button disabled>Create rule</Button>
        </div>
      </div>

      <Card className="p-4">
        <div className="text-sm text-[var(--color-text-secondary)]">No risk data to display — backend integration pending.</div>
      </Card>

      <div className="mt-6">
        <UpcomingFeature title="Fraud & Risk Management" description="Create and configure fraud detection and risk rules. Editing disabled until backend APIs exist." />
      </div>
    </div>
  );
};
