import { useState } from 'react';
import { Shield, Menu, X, LogOut, LayoutDashboard, FileText, FolderOpen, User as UserIcon, ChevronDown } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { navigate } from '@/lib/router';

export function Navbar() {
  const { isAuthenticated, fullName, customerId, signOut } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenu, setUserMenu] = useState(false);

  console.log('[Navbar] Auth state:', { isAuthenticated, fullName, customerId });

  const navLink = (path: string, label: string) => (
    <button
      onClick={() => { navigate(path); setMobileOpen(false); }}
      className="text-sm font-medium text-slate-600 hover:text-blue-600 transition-colors"
    >
      {label}
    </button>
  );

  const handleSignOut = async () => {
    await signOut();
    navigate('/');
  };

  return (
    <nav className="sticky top-0 z-50 bg-white/80 backdrop-blur-lg border-b border-slate-200/60">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <button onClick={() => navigate('/')} className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-blue-500 to-blue-700 flex items-center justify-center shadow-sm shadow-blue-600/30">
              <Shield className="w-5 h-5 text-white" strokeWidth={2.5} />
            </div>
            <span className="text-lg font-bold text-slate-900 tracking-tight">ClaimAssist</span>
          </button>

          {/* Desktop nav */}
          <div className="hidden md:flex items-center gap-7">
            {navLink('/', 'Home')}
            {navLink('/products', 'Products')}
            {navLink('/claims', 'Claims')}
            {navLink('/about', 'About')}
            {navLink('/contact', 'Contact')}
          </div>

          {/* Right side */}
          <div className="hidden md:flex items-center gap-3">
            {isAuthenticated ? (
              <div className="relative">
                <button
                  onClick={() => setUserMenu(!userMenu)}
                  className="flex items-center gap-2 px-3 py-1.5 rounded-lg hover:bg-slate-100 transition-colors"
                >
                  <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center text-blue-700 font-semibold text-sm">
                    {(fullName || 'U')[0].toUpperCase()}
                  </div>
                  <ChevronDown className="w-4 h-4 text-slate-400" />
                </button>
                {userMenu && (
                  <>
                    <div className="fixed inset-0 z-10" onClick={() => setUserMenu(false)} />
                    <div className="absolute right-0 mt-2 w-56 bg-white rounded-xl shadow-lg border border-slate-200 py-1.5 z-20">
                      <div className="px-4 py-2 border-b border-slate-100">
                        <p className="text-sm font-semibold text-slate-900 truncate">{fullName || 'Account'}</p>
                        <p className="text-xs text-slate-500 truncate">{customerId}</p>
                      </div>
                      <button onClick={() => { navigate('/dashboard'); setUserMenu(false); }} className="w-full flex items-center gap-2.5 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors">
                        <LayoutDashboard className="w-4 h-4 text-slate-400" /> Dashboard
                      </button>
                      <button onClick={() => { navigate('/policies'); setUserMenu(false); }} className="w-full flex items-center gap-2.5 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors">
                        <FolderOpen className="w-4 h-4 text-slate-400" /> Policies
                      </button>
                      <button onClick={() => { navigate('/claims-list'); setUserMenu(false); }} className="w-full flex items-center gap-2.5 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors">
                        <FileText className="w-4 h-4 text-slate-400" /> Claims
                      </button>
                      <button onClick={() => { navigate('/profile'); setUserMenu(false); }} className="w-full flex items-center gap-2.5 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors">
                        <UserIcon className="w-4 h-4 text-slate-400" /> Profile
                      </button>
                      <div className="border-t border-slate-100 mt-1.5 pt-1.5">
                        <button onClick={handleSignOut} className="w-full flex items-center gap-2.5 px-4 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors">
                          <LogOut className="w-4 h-4" /> Sign Out
                        </button>
                      </div>
                    </div>
                  </>
                )}
              </div>
            ) : (
              <>
                <button onClick={() => navigate('/login')} className="text-sm font-medium text-slate-600 hover:text-blue-600 transition-colors px-3 py-2">
                  Sign In
                </button>
                <button onClick={() => navigate('/register')} className="text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 px-5 py-2 rounded-lg transition-colors shadow-sm shadow-blue-600/20">
                  Get Started
                </button>
              </>
            )}
          </div>

          {/* Mobile toggle */}
          <button className="md:hidden p-2 -mr-2 text-slate-600" onClick={() => setMobileOpen(!mobileOpen)}>
            {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>

        {/* Mobile menu */}
        {mobileOpen && (
          <div className="md:hidden border-t border-slate-200 py-4 space-y-3">
            <button onClick={() => { navigate('/'); setMobileOpen(false); }} className="block w-full text-left text-sm font-medium text-slate-600 hover:text-blue-600">Home</button>
            <button onClick={() => { navigate('/products'); setMobileOpen(false); }} className="block w-full text-left text-sm font-medium text-slate-600 hover:text-blue-600">Products</button>
            <button onClick={() => { navigate('/claims'); setMobileOpen(false); }} className="block w-full text-left text-sm font-medium text-slate-600 hover:text-blue-600">Claims</button>
            <button onClick={() => { navigate('/about'); setMobileOpen(false); }} className="block w-full text-left text-sm font-medium text-slate-600 hover:text-blue-600">About</button>
            <button onClick={() => { navigate('/contact'); setMobileOpen(false); }} className="block w-full text-left text-sm font-medium text-slate-600 hover:text-blue-600">Contact</button>
            <div className="pt-3 border-t border-slate-200">
              {isAuthenticated ? (
                <>
                  <button onClick={() => { navigate('/dashboard'); setMobileOpen(false); }} className="block w-full text-left text-sm font-medium text-slate-600 hover:text-blue-600 mb-2">Dashboard</button>
                  <button onClick={() => { handleSignOut(); setMobileOpen(false); }} className="block w-full text-left text-sm font-medium text-red-600">Sign Out</button>
                </>
              ) : (
                <div className="flex gap-2">
                  <button onClick={() => { navigate('/login'); setMobileOpen(false); }} className="flex-1 text-sm font-medium text-blue-600 border border-blue-200 py-2 rounded-lg">Sign In</button>
                  <button onClick={() => { navigate('/register'); setMobileOpen(false); }} className="flex-1 text-sm font-semibold text-white bg-blue-600 py-2 rounded-lg">Get Started</button>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </nav>
  );
}
