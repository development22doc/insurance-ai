import React from 'react';
import { Link } from 'react-router-dom';
import { Button, Card, Badge } from '../components/ui';

export const HomePage: React.FC = () => {
  return (
    <div>
      {/* Hero */}
      <section className="bg-gradient-to-r from-blue-50 to-indigo-50 py-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
            <div>
              <h1 className="text-4xl sm:text-5xl font-bold text-[var(--color-text-primary)] leading-tight">
                Faster claims. Fairer outcomes.
              </h1>
              <p className="mt-6 text-lg text-[var(--color-text-secondary)] max-w-xl">
                ClaimAssist streamlines insurance claims with secure document handling,
                AI-assisted assessment, and clear tracking so customers and adjusters stay aligned.
              </p>

              <div className="mt-8 flex flex-col sm:flex-row gap-4">
                <Link to="/products">
                  <Button size="lg" className="min-w-[180px]">Explore Products</Button>
                </Link>
                <Link to="/login">
                  <Button variant="ghost" size="lg" className="min-w-[180px]">Sign In</Button>
                </Link>
              </div>

              <div className="mt-8 flex flex-wrap items-center gap-4">
                <Badge className="px-3 py-1">AI-assisted analysis</Badge>
                <span className="text-sm text-[var(--color-text-secondary)]">•</span>
                <Badge className="px-3 py-1">Secure document upload</Badge>
                <span className="text-sm text-[var(--color-text-secondary)]">•</span>
                <Badge className="px-3 py-1">Transparent tracking</Badge>
              </div>
            </div>

            <div>
              <Card padding="lg" shadow="lg" className="w-full">
                <img
                  src="/assets/hero.png"
                  alt="Illustration of ClaimAssist claims flow"
                  className="w-full h-64 object-cover rounded-md"
                />
                <div className="mt-4">
                  <h3 className="text-xl font-semibold">One place to manage claims</h3>
                  <p className="text-[var(--color-text-secondary)] mt-2">From filing to decision — track progress and upload documents securely.</p>
                </div>
              </Card>
            </div>
          </div>

          {/* Product overview */}
          <div className="mt-16">
            <h2 className="text-2xl font-bold">Our products</h2>
            <p className="text-[var(--color-text-secondary)] mt-2 max-w-2xl">Insurance products designed around claims-first customer experience.</p>

            <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
              <Card padding="md">
                <h4 className="text-lg font-semibold">Vehicle Insurance</h4>
                <p className="mt-2 text-[var(--color-text-secondary)]">Comprehensive vehicle protection with streamlined claim handling and repair workflows.</p>
                <div className="mt-4">
                  <Link to="/products">
                    <Button size="sm">Learn more</Button>
                  </Link>
                </div>
              </Card>

              <Card padding="md">
                <h4 className="text-lg font-semibold">Health Insurance</h4>
                <p className="mt-2 text-[var(--color-text-secondary)]">Quick processing for medical claims with document upload and AI-assisted extraction.</p>
                <div className="mt-4">
                  <Link to="/products">
                    <Button size="sm">Learn more</Button>
                  </Link>
                </div>
              </Card>

              <Card padding="md">
                <h4 className="text-lg font-semibold">Travel Insurance</h4>
                <p className="mt-2 text-[var(--color-text-secondary)]">Coverage for trip interruptions and on-the-go claim filing.</p>
                <div className="mt-4">
                  <Link to="/products">
                    <Button size="sm">Learn more</Button>
                  </Link>
                </div>
              </Card>
            </div>
          </div>

          {/* How it works */}
          <div className="mt-16">
            <h2 className="text-2xl font-bold">How ClaimAssist works</h2>
            <div className="mt-6 grid grid-cols-1 md:grid-cols-3 gap-6">
              <Card padding="md">
                <h4 className="font-semibold">1. File a claim</h4>
                <p className="text-[var(--color-text-secondary)] mt-2">Start with a simple guided report and upload photos or documents.</p>
              </Card>
              <Card padding="md">
                <h4 className="font-semibold">2. AI-assisted review</h4>
                <p className="text-[var(--color-text-secondary)] mt-2">Automated checks help prioritize issues and extract key information.</p>
              </Card>
              <Card padding="md">
                <h4 className="font-semibold">3. Track & settle</h4>
                <p className="text-[var(--color-text-secondary)] mt-2">Follow progress and receive decisions with clear next steps.</p>
              </Card>
            </div>
          </div>

          {/* CTA */}
          <div className="mt-16 text-center">
            <h3 className="text-xl font-bold">Ready to get started?</h3>
            <div className="mt-4 flex items-center justify-center gap-4">
              <Link to="/register">
                <Button size="lg">Create an account</Button>
              </Link>
              <Link to="/login">
                <Button variant="ghost" size="lg">Sign in</Button>
              </Link>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};
