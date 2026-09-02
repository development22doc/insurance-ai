import React from 'react';
import { Card } from './ui/Card';
import { Button } from './ui/Button';

interface UpcomingFeatureProps {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  category?: string;
  children?: React.ReactNode;
}

export const UpcomingFeature: React.FC<UpcomingFeatureProps> = ({ title, description, icon, category, children }) => {
  return (
    <Card className="max-w-4xl mx-auto p-6">
      <div className="flex items-start space-x-4">
        <div className="text-4xl select-none">
          {icon ?? '🚧'}
        </div>
        <div className="flex-1">
          <h1 className="text-2xl font-semibold">{title}</h1>
          {category && <div className="text-sm text-[var(--color-text-secondary)] mt-1">{category}</div>}
          <p className="mt-4 text-[var(--color-text-secondary)]">{description}</p>

          <div className="mt-6 space-y-3">
            <div className="px-4 py-3 bg-yellow-50 border border-yellow-200 rounded">
              <strong>Coming Soon</strong>
              <div className="text-sm text-[var(--color-text-secondary)]">Backend integration is pending. Actions on this page are disabled until server APIs are available.</div>
            </div>

            <div className="flex flex-wrap gap-3 mt-3">
              <Button disabled>Create</Button>
              <Button disabled>Save</Button>
              <Button disabled>Delete</Button>
            </div>

            {children}
          </div>
        </div>
      </div>
    </Card>
  );
};
