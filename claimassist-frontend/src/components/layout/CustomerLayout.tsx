import React, { useState } from 'react';
import { Link, Outlet } from 'react-router-dom';

export const CustomerLayout: React.FC = () => {
  const [isSidebarOpen, setSidebarOpen] = useState(false);

  const navigation = [
    { name: 'Dashboard', href: '/dashboard' },
    { name: 'Policies', href: '/policies' },
    { name: 'Claims', href: '/claims' },
    { name: 'Documents', href: '/documents' },
    { name: 'Notifications', href: '/notifications' },
    { name: 'Profile', href: '/profile' },
  ];

  return (
    <div className="min-h-screen bg-[var(--color-surface)]">
      {/* Mobile header */}
      <div className="md:hidden bg-[var(--color-background)] border-b border-[var(--color-border)] p-4">
        <div className="flex items-center justify-between">
          <span className="text-xl font-bold text-[var(--color-primary)]">
            ClaimAssist
          </span>
          <button
            onClick={() => setSidebarOpen(true)}
            className="p-2 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
            aria-label="Open menu"
          >
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
        </div>
      </div>

      {/* Mobile sidebar overlay */}
      {isSidebarOpen && (
        <div
          className="fixed inset-0 z-50 bg-black bg-opacity-50 md:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 w-64 bg-[var(--color-background)] border-r border-[var(--color-border)] transform transition-transform duration-300 ease-in-out md:translate-x-0 md:static md:inset-0 ${
          isSidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex flex-col h-full">
          <div className="p-6 border-b border-[var(--color-border)]">
            <span className="text-2xl font-bold text-[var(--color-primary)]">
              ClaimAssist
            </span>
          </div>

          <nav className="flex-1 p-4 space-y-1">
            {navigation.map((item) => (
              <Link
                key={item.name}
                to={item.href}
                className="block px-4 py-2 text-sm font-medium text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] hover:bg-[var(--color-surface)] rounded-lg transition-colors"
                onClick={() => setSidebarOpen(false)}
              >
                {item.name}
              </Link>
            ))}
          </nav>

          <div className="p-4 border-t border-[var(--color-border)]">
            <Link
              to="/logout"
              className="block px-4 py-2 text-sm font-medium text-[var(--color-danger)] hover:bg-[var(--color-danger-light)] rounded-lg transition-colors"
            >
              Logout
            </Link>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <div className="md:pl-64">
        <main className="p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
