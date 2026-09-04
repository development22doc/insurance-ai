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
    <>
      {/* Utility Bar */}
      <div className="utility-bar">
        <div className="container">
          <div className="utility-bar-links">
            <a href="#grievance">Grievance Support</a>
            <a href="#partner">Partner With Us</a>
            <a href="#resources">Resources</a>
          </div>
          <div className="utility-bar-links">
            <a href="#help">Help</a>
            <a href="#accessibility">Accessibility</a>
            <a href="#language">English ▼</a>
          </div>
        </div>
      </div>

      {/* Main Navigation */}
      <header className="nav-bar">
        <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', height: '100%' }}>
          <Link to="/" className="nav-logo">
            ClaimAssist
          </Link>

          {showNavigation && (
            <nav className="nav-links">
              {!isAuthenticated && (
                <>
                  <Link to="/products">Products</Link>
                  <Link to="/claims">Claims</Link>
                  <Link to="/about">About</Link>
                  <Link to="/contact">Contact</Link>
                </>
              )}
              {isAuthenticated && hasRole('CUSTOMER') && (
                <>
                  <Link to="/dashboard">Dashboard</Link>
                  <Link to="/policies">Policies</Link>
                  <Link to="/claims">Claims</Link>
                </>
              )}
              {isAuthenticated && (hasRole('ADJUSTER') || hasRole('AUDITOR')) && (
                <>
                  <Link to="/operations">Operations</Link>
                </>
              )}
              {isAuthenticated && (hasRole('ADMIN') || hasRole('SUPPORT')) && (
                <>
                  <Link to="/admin">Admin</Link>
                </>
              )}
            </nav>
          )}

          <div className="nav-right">
            {!isAuthenticated ? (
              <>
                <a href="#language" style={{ marginRight: '1.5rem', color: 'var(--text-secondary)' }}>English</a>
                <Link to="/login" className="nav-login">Login</Link>
                <Link to="/register" className="nav-register">Register</Link>
              </>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <span style={{ fontSize: '14px', color: 'var(--text-secondary)' }}>
                  {user?.fullName}
                </span>
                <button
                  onClick={handleLogout}
                  className="nav-login"
                  style={{ margin: 0 }}
                >
                  Sign Out
                </button>
              </div>
            )}
          </div>
        </div>
      </header>
    </>
  );
};
