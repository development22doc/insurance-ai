import React from 'react';
import { UpcomingFeature } from '../components/UpcomingFeature';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';

export const AdminProductsPage: React.FC = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-2">Product Administration</h1>
      <p className="text-[var(--color-text-secondary)] mb-4">Product administration preview. Backend product management APIs are coming soon.</p>

      <div className="mb-4 flex justify-between items-center">
        <div className="flex gap-3">
          <input className="border rounded px-3 py-2" placeholder="Search products" disabled />
          <select className="border rounded px-3 py-2" disabled>
            <option>All types</option>
          </select>
        </div>
        <div>
          <Button disabled>Create Product</Button>
        </div>
      </div>

      <Card className="p-4">
        <div className="text-sm text-[var(--color-text-secondary)]">No product data available in preview.</div>
        <div className="mt-4">
          <table className="w-full text-left table-auto text-sm">
            <thead>
              <tr className="text-[var(--color-text-secondary)]">
                <th className="p-2">Product</th>
                <th className="p-2">Type</th>
                <th className="p-2">Status</th>
                <th className="p-2">Coverage</th>
                <th className="p-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td colSpan={5} className="p-6 text-center">No products to display — backend integration pending.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </Card>

      <div className="mt-6">
        <UpcomingFeature title="Product Administration" description="Create and manage insurance products. Actions are disabled in preview." />
      </div>
    </div>
  );
};
