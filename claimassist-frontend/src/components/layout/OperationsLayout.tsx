import React, { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';

export const OperationsLayout: React.FC = () => {
  const [isSidebarOpen, setSidebarOpen] = useState(false);
  const { user, logout } = useAuth();

  const navigation = [
    { name: 'Dashboard', href: '/operations' },
    { name: 'Claims Queue', href: '/operations/claims' },
  ];

  const handleLogout = async () => {
    await logout();
  };

  return (
    <div className="min-h-screen bg-[var(--color-surface)]">
      <div className="md:hidden bg-[var(--color-background)] border-b border-[var(--color-border)] p-4">
        <div className="flex items-center justify-between">
          <div>
            <span className="text-xl font-bold text-[var(--color-secondary)]">ClaimAssist Operations</span>
            {user && <p className="text-xs text-[var(--color-text-secondary)]">{user.fullName}</p>}
          </div>
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

      {isSidebarOpen && (
        <div
          className="fixed inset-0 z-50 bg-black bg-opacity-50 md:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-50 w-64 bg-[var(--color-background)] border-r border-[var(--color-border)] transform transition-transform duration-300 ease-in-out md:translate-x-0 md:static md:inset-0 ${
          isSidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex flex-col h-full">
          <div className="p-6 border-b border-[var(--color-border)]">
            <div className="text-2xl font-bold text-[var(--color-secondary)]">ClaimAssist</div>
            <div className="mt-1 text-sm text-[var(--color-text-secondary)]">Operations</div>
          </div>

          <nav className="flex-1 p-4 space-y-1">
            {navigation.map((item) => (
              <NavLink
                key={item.name}
                to={item.href}
                end={item.href === '/operations'}
                className={({ isActive }) =>
                  `block rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-[var(--color-primary)] text-white'
                      : 'text-[var(--color-text-secondary)] hover:text-[var(--color-secondary)] hover:bg-[var(--color-surface)]'
                  }`
                }
                onClick={() => setSidebarOpen(false)}
              >
                {item.name}
              </NavLink>
            ))}
          </nav>

          <div className="p-4 border-t border-[var(--color-border)]">
            <div className="mb-3 rounded-lg bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-text-secondary)]">
              {user?.fullName ?? 'Operations user'}
            </div>
            <button
              type="button"
              onClick={() => void handleLogout()}
              className="block w-full rounded-lg px-4 py-2 text-left text-sm font-medium text-[var(--color-danger)] hover:bg-[var(--color-danger-light)] transition-colors"
            >
              Logout
            </button>
          </div>
        </div>
      </aside>

      <div className="md:pl-64">
        <main className="p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
