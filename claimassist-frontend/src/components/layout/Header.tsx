import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';

export interface HeaderProps {
  showNavigation?: boolean;
}

export const Header: React.FC<HeaderProps> = ({ showNavigation = true }) => {
  const { isAuthenticated, user, logout, hasRole } = useAuth();

  const handleLogout = async () => {
    await logout();
  };

  return (
    <header className="bg-[var(--color-background)] border-b border-[var(--color-border)] sticky top-0 z-[var(--z-sticky)]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <Link to="/" className="flex items-center">
            <div className="flex-shrink-0">
              <span className="text-2xl font-bold text-[var(--color-primary)]">
                ClaimAssist
              </span>
            </div>
          </Link>

          {showNavigation && (
            <nav className="hidden md:flex space-x-8">
              {!isAuthenticated && (
                <>
                  <Link
                    to="/products"
                    className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                  >
                    Products
                  </Link>
                  <Link
                    to="/claims"
                    className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                  >
                    Claims
                  </Link>
                  <Link
                    to="/about"
                    className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                  >
                    About
                  </Link>
                  <Link
                    to="/contact"
                    className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                  >
                    Contact
                  </Link>
                </>
              )}
              {isAuthenticated && hasRole('CUSTOMER') && (
                <>
                  <Link
                    to="/dashboard"
                    className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                  >
                    Dashboard
                  </Link>
                  <Link
                    to="/policies"
                    className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                  >
                    Policies
                  </Link>
                  <Link
                    to="/claims"
                    className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                  >
                    Claims
                  </Link>
                </>
              )}
              {isAuthenticated && (hasRole('ADJUSTER') || hasRole('AUDITOR')) && (
                <>
                  <Link
                    to="/operations"
                    className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                  >
                    Operations
                  </Link>
                </>
              )}
            </nav>
          )}

          <div className="flex items-center space-x-4">
            {!isAuthenticated ? (
              <>
                <Link
                  to="/login"
                  className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                >
                  Login
                </Link>
                <Link
                  to="/register"
                  className="bg-[var(--color-primary)] text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-colors"
                >
                  Register
                </Link>
              </>
            ) : (
              <div className="flex items-center space-x-4">
                <span className="text-sm text-[var(--color-text-secondary)]">
                  {user?.fullName}
                </span>
                <button
                  onClick={handleLogout}
                  className="text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] px-3 py-2 text-sm font-medium transition-colors"
                >
                  Sign Out
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};
