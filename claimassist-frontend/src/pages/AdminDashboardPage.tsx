import React from 'react';
import { Link } from 'react-router-dom';
import { Card } from '../components/ui/Card';
import { UpcomingFeature } from '../components/UpcomingFeature';

export const AdminDashboardPage: React.FC = () => {
  const cards = [
    { title: 'Users', path: '/admin/users' },
    { title: 'RBAC', path: '/admin/rbac' },
    { title: 'Products', path: '/admin/products' },
    { title: 'AI', path: '/admin/ai' },
    { title: 'Fraud & Risk', path: '/admin/risk' },
    { title: 'Audit', path: '/admin/audit' },
    { title: 'Documents', path: '/admin/documents' },
  ];

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4">Admin Dashboard</h1>
      <p className="text-[var(--color-text-secondary)] mb-6">Preview-only admin console. All modules are coming soon and are read-only placeholders until backend APIs are implemented.</p>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {cards.map(c => (
          <Card key={c.path} className="p-4 flex flex-col justify-between">
            <div>
              <h2 className="text-lg font-semibold">{c.title}</h2>
              <p className="text-sm text-[var(--color-text-secondary)] mt-2">Backend integration pending</p>
            </div>
            <div className="mt-4">
              <Link to={c.path} className="inline-block text-sm text-[var(--color-primary)]">Open</Link>
            </div>
          </Card>
        ))}
      </div>

      <div className="mt-8">
        <UpcomingFeature title="Admin Hub — Preview" description="These pages are fully designed and accessible to ADMIN or SUPPORT roles as a preview only. No backend calls are performed." icon="🛡️" />
      </div>
    </div>
  );
};
