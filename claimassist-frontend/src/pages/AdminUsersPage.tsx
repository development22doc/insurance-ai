import React from 'react';
import { UpcomingFeature } from '../components/UpcomingFeature';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';

export const AdminUsersPage: React.FC = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-2">User Management</h1>
      <p className="text-[var(--color-text-secondary)] mb-4">User management is coming soon. Backend administration APIs are currently being implemented.</p>

      <div className="mb-4 flex flex-col sm:flex-row gap-3">
        <Input placeholder="Search users by name or email" disabled />
        <div className="flex items-center">
          <Button disabled>Search</Button>
        </div>
      </div>

      <Card className="p-4">
        <div className="mb-4 flex items-center justify-between">
          <div className="text-sm text-[var(--color-text-secondary)]">No user data available in preview</div>
          <div>
            <Button disabled>Create user</Button>
          </div>
        </div>

        <div className="w-full overflow-auto">
          <table className="w-full text-left table-auto">
            <thead>
              <tr className="text-sm text-[var(--color-text-secondary)]">
                <th className="p-2">Name</th>
                <th className="p-2">Email</th>
                <th className="p-2">Status</th>
                <th className="p-2">Roles</th>
                <th className="p-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td colSpan={5} className="p-6 text-center text-[var(--color-text-secondary)]">No users to display — backend integration pending.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </Card>

      <div className="mt-6">
        <UpcomingFeature title="User Management" description="Create, edit, reset passwords and manage user roles. All actions are disabled in preview." />
      </div>
    </div>
  );
};
