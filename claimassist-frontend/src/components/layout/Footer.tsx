import React from 'react';
import { Link } from 'react-router-dom';

export const Footer: React.FC = () => {
  return (
    <footer className="bg-[var(--color-surface)] border-t border-[var(--color-border)]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          <div>
            <h3 className="text-lg font-semibold text-[var(--color-text-primary)] mb-4">
              ClaimAssist
            </h3>
            <p className="text-[var(--color-text-secondary)] text-sm">
              Modern insurance claims management powered by AI.
            </p>
          </div>

          <div>
            <h4 className="text-sm font-semibold text-[var(--color-text-primary)] mb-4">
              Products
            </h4>
            <ul className="space-y-2">
              <li>
                <Link to="/products" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] text-sm">
                  Health Insurance
                </Link>
              </li>
              <li>
                <Link to="/products" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] text-sm">
                  Vehicle Insurance
                </Link>
              </li>
              <li>
                <Link to="/products" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] text-sm">
                  Travel Insurance
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <h4 className="text-sm font-semibold text-[var(--color-text-primary)] mb-4">
              Company
            </h4>
            <ul className="space-y-2">
              <li>
                <Link to="/about" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] text-sm">
                  About Us
                </Link>
              </li>
              <li>
                <Link to="/contact" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] text-sm">
                  Contact
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <h4 className="text-sm font-semibold text-[var(--color-text-primary)] mb-4">
              Legal
            </h4>
            <ul className="space-y-2">
              <li>
                <a href="#" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] text-sm">
                  Privacy Policy
                </a>
              </li>
              <li>
                <a href="#" className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] text-sm">
                  Terms of Service
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-8 pt-8 border-t border-[var(--color-border)]">
          <p className="text-center text-[var(--color-text-muted)] text-sm">
            © {new Date().getFullYear()} ClaimAssist. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
};
