import React from 'react';
import { UpcomingFeature } from '../components/UpcomingFeature';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';

export const AdminAiPage: React.FC = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold">AI Configuration</h1>
      <p className="text-[var(--color-text-secondary)] mb-4">AI model and agent configuration preview. All changes are disabled until backend APIs provide secure configuration endpoints.</p>

      <Card className="p-4 max-w-xl">
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium">Active model</label>
            <div className="mt-1 text-[var(--color-text-secondary)]">Not available in preview</div>
          </div>

          <div>
            <label className="block text-sm font-medium">Temperature</label>
            <input className="border rounded px-3 py-2 w-40" disabled />
          </div>

          <div>
            <label className="block text-sm font-medium">Max tokens</label>
            <input className="border rounded px-3 py-2 w-40" disabled />
          </div>

          <div>
            <label className="block text-sm font-medium">Enable Agent</label>
            <div className="mt-1 text-[var(--color-text-secondary)]">Preview only</div>
          </div>

          <div className="mt-4 flex gap-3">
            <Button disabled>Save configuration</Button>
            <Button disabled>Revert</Button>
          </div>
        </div>
      </Card>

      <div className="mt-6">
        <UpcomingFeature title="AI Configuration" description="Configure models, prompts and thresholds. Editing is disabled in preview." />
      </div>
    </div>
  );
};
