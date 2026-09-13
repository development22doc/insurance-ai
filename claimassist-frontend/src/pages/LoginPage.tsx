import { useEffect } from 'react';
import { Shield, Mail, Lock, ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Field, Input } from '@/components/ui/Field';
import { useAuth } from '@/context/AuthContext';
import { navigate } from '@/lib/router';

export function LoginPage() {
  const { signIn, isAuthenticated } = useAuth();

  useEffect(() => {
    if (isAuthenticated) navigate('/dashboard');
  }, [isAuthenticated]);

  const handleSubmit = (e: React.FormEvent) => {
    console.log('LOGIN_CLICK_HANDLER = YES');
    e.preventDefault();
    console.log('FORM_SUBMIT_HANDLER = YES');
    // Redirect to Keycloak authorization flow
    signIn();
    console.log('SIGN_IN_CALLED = YES');
  };

  return (
    <div className="min-h-screen flex">
      {/* Left side - form */}
      <div className="flex-1 flex items-center justify-center px-4 py-12 bg-white">
        <div className="w-full max-w-sm">
          <button onClick={() => navigate('/')} className="flex items-center gap-2.5 mb-8">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-blue-700 flex items-center justify-center shadow-sm shadow-blue-600/30">
              <Shield className="w-5 h-5 text-white" strokeWidth={2.5} />
            </div>
            <span className="text-xl font-bold text-slate-900 tracking-tight">ClaimAssist</span>
          </button>

          <h1 className="text-2xl font-bold text-slate-900 mb-2">Welcome back</h1>
          <p className="text-sm text-slate-500 mb-8">Sign in to manage your policies and claims.</p>

          <form onSubmit={handleSubmit} className="space-y-4">
            <Field label="Email">
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <Input
                  type="email"
                  placeholder="you@example.com"
                  className="pl-10"
                />
              </div>
            </Field>
            <Field label="Password">
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <Input
                  type="password"
                  placeholder="••••••••"
                  className="pl-10"
                />
              </div>
            </Field>
            <Button
              type="submit"
              size="lg"
              className="w-full"
            >
              Sign In <ArrowRight className="w-4 h-4" />
            </Button>
          </form>

          <p className="text-sm text-slate-500 text-center mt-6">
            Don't have an account?{' '}
            <button onClick={() => navigate('/register')} className="font-semibold text-blue-600 hover:text-blue-700 transition-colors">
              Sign up free
            </button>
          </p>
        </div>
      </div>

      {/* Right side - visual */}
      <div className="hidden lg:flex flex-1 bg-gradient-to-br from-navy-900 via-navy-800 to-blue-900 relative overflow-hidden items-center justify-center p-12">
        <div className="absolute inset-0 opacity-10" style={{ backgroundImage: 'radial-gradient(circle at 30% 50%, #60a5fa 0%, transparent 50%), radial-gradient(circle at 70% 50%, #3b82f6 0%, transparent 50%)' }} />
        <div className="relative max-w-md text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-white/10 backdrop-blur mb-6">
            <Shield className="w-8 h-8 text-blue-300" strokeWidth={2} />
          </div>
          <h2 className="text-3xl font-bold text-white mb-4 tracking-tight">Your insurance, simplified</h2>
          <p className="text-slate-300 leading-relaxed mb-8">
            Manage policies, file claims, and get AI-powered assistance — all from one beautiful dashboard.
          </p>
        </div>
      </div>
    </div>
  );
}
