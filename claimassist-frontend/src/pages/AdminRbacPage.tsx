import React from 'react';
import { UpcomingFeature } from '../components/UpcomingFeature';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';

const roles = ['CUSTOMER', 'ADJUSTER', 'AUDITOR', 'ADMIN', 'SUPPORT'];
const permissionsPreview = ['Claim:View', 'Claim:Edit', 'User:Manage', 'Product:Manage'];

export const AdminRbacPage: React.FC = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold">RBAC Management</h1>
      <p className="text-[var(--color-text-secondary)] mb-4">Role and permission management preview. No changes can be saved until backend APIs are available.</p>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="p-4">
          <h2 className="font-semibold">Roles</h2>
          <ul className="mt-3 space-y-2 text-sm">
            {roles.map(r => (
              <li key={r} className="flex items-center justify-between">
                <div>{r}</div>
                <div className="text-[var(--color-text-secondary)] text-sm">Preview</div>
              </li>
            ))}
          </ul>
        </Card>

        <Card className="p-4 md:col-span-2">
          <h2 className="font-semibold">Permission Matrix (Preview)</h2>
          <div className="overflow-auto mt-3">
            <table className="w-full table-auto text-sm">
              <thead>
                <tr>
                  <th className="p-2 text-left">Permission</th>
                  {roles.map(r => <th key={r} className="p-2 text-left">{r}</th>)}
                </tr>
              </thead>
              <tbody>
                {permissionsPreview.map(p => (
                  <tr key={p} className="border-t">
                    <td className="p-2">{p}</td>
                    {roles.map(r => (
                      <td key={r+p} className="p-2">—</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex items-center gap-3">
            <Button disabled>Assign Permission</Button>
            <Button disabled>Save Changes</Button>
          </div>
        </Card>
      </div>

      <div className="mt-6">
        <UpcomingFeature title="RBAC Management" description="Manage roles and permissions for ClaimAssist. Changes are disabled until backend support exists." />
      </div>
    </div>
  );
};
