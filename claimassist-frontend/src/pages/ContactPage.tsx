import React from 'react';
import { Card, Button } from '../components/ui';
import { Link } from 'react-router-dom';

export const ContactPage: React.FC = () => {
  return (
    <div className="py-16">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        <h1 className="text-3xl font-bold">Contact</h1>
        <p className="mt-2 text-[var(--color-text-secondary)]">For support and inquiries, sign in to your account so we can route your request securely. For general information, the resources below may help.</p>

        <div className="mt-8 grid grid-cols-1 md:grid-cols-2 gap-6">
          <Card padding="md">
            <h3 className="text-xl font-semibold">Support</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">If you're a customer, please sign in and use the support options in your dashboard so requests can be linked to your policies and claims.</p>
            <div className="mt-4">
              <Link to="/login"><Button size="sm">Sign in to support</Button></Link>
            </div>
          </Card>

          <Card padding="md">
            <h3 className="text-xl font-semibold">Sales & Partnerships</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">For product information and integrations, please register for a business account.</p>
            <div className="mt-4">
              <Link to="/register"><Button size="sm">Register</Button></Link>
            </div>
          </Card>
        </div>

        <div className="mt-8">
          <Card padding="md">
            <h3 className="text-xl font-semibold">Privacy & Security</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">We prioritise secure handling of personal data. Authentication and authorization are handled through the existing Keycloak flow and backend access controls.</p>
          </Card>
        </div>
      </div>
    </div>
  );
};
