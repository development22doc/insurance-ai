import React from 'react';
import { Card } from '../components/ui';

export const AboutPage: React.FC = () => {
  return (
    <div className="py-16">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        <h1 className="text-3xl font-bold">About ClaimAssist</h1>
        <p className="mt-2 text-[var(--color-text-secondary)]">ClaimAssist builds focused tools to simplify insurance claims for customers and operations teams alike.</p>

        <div className="mt-8 grid grid-cols-1 md:grid-cols-2 gap-6">
          <Card padding="md">
            <h3 className="text-xl font-semibold">Our mission</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">Make claims fair, transparent, and fast through better workflows and applied machine learning where it helps reviewers.</p>
          </Card>

          <Card padding="md">
            <h3 className="text-xl font-semibold">What we build</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">A claims-first platform: filing, secure document management, AI-assisted extraction, role-based operations, and clear tracking.</p>
          </Card>
        </div>

        <div className="mt-8">
          <Card padding="md">
            <h3 className="text-xl font-semibold">AI in ClaimAssist</h3>
            <p className="mt-2 text-[var(--color-text-secondary)]">We use AI to assist with information extraction and prioritization — not to replace human adjusters. Final decisions and reviews remain with trained staff.</p>
          </Card>
        </div>
      </div>
    </div>
  );
};
