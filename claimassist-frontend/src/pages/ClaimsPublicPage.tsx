import React from 'react';
import { Link } from 'react-router-dom';
import { Card, Button } from '../components/ui';

export const ClaimsPublicPage: React.FC = () => {
  return (
    <div className="py-16">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        <h1 className="text-3xl font-bold">Claims</h1>
        <p className="mt-2 text-[var(--color-text-secondary)]">Learn how ClaimAssist helps customers through the claims journey.</p>

        <div className="mt-8 space-y-6">
          <Card padding="md">
            <h3 className="text-xl font-semibold">File a claim</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">Start a claim with a guided form and upload photos or documents for faster processing.</p>
            <div className="mt-4">
              <Link to="/login"><Button size="sm">Sign in to file a claim</Button></Link>
            </div>
          </Card>

          <Card padding="md">
            <h3 className="text-xl font-semibold">AI-assisted analysis</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">Our AI helps extract details from documents to speed up review. Human adjusters make final decisions.</p>
          </Card>

          <Card padding="md">
            <h3 className="text-xl font-semibold">Track progress</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">Track who is working on your claim, what documents are needed, and the current status.</p>
          </Card>

          <Card padding="md">
            <h3 className="text-xl font-semibold">Security & privacy</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">Documents are transmitted over TLS and stored according to backend policies. Access is controlled by user authentication.</p>
          </Card>
        </div>

        <div className="mt-12 text-center">
          <Link to="/register"><Button size="lg">Create an account</Button></Link>
        </div>
      </div>
    </div>
  );
};
