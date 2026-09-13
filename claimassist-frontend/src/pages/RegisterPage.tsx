import { useState, useEffect } from 'react';
import { Shield, Mail, Lock, User, ArrowRight, AlertCircle, Check } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Field, Input } from '@/components/ui/Field';
import { useAuth } from '@/context/AuthContext';
import { navigate } from '@/lib/router';

export function RegisterPage() {
  const { signUp, isAuthenticated } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (isAuthenticated) navigate('/dashboard');
  }, [isAuthenticated]);

  const passwordChecks = [
    { label: 'At least 8 characters', valid: password.length >= 8 },
    { label: 'Contains a letter', valid: /[a-zA-Z]/.test(password) },
    { label: 'Contains a number', valid: /\d/.test(password) },
  ];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) { setError('Please enter your full name.'); return; }
    if (!email.trim()) { setError('Please enter your email.'); return; }
    if (password.length < 8) { setError('Password must be at least 8 characters.'); return; }
    setLoading(true);
    const { error: signUpError } = await signUp(email, name, password);
    setLoading(false);
    if (signUpError) {
      setError(signUpError);
    } else {
      // Successful registration - redirect to login for sign in
      navigate('/login');
    }
  };

  return (
    <div className="min-h-screen flex">
      {/* Left side - visual */}
      <div className="hidden lg:flex flex-1 bg-gradient-to-br from-navy-900 via-navy-800 to-blue-900 relative overflow-hidden items-center justify-center p-12">
        <div className="absolute inset-0 opacity-10" style={{ backgroundImage: 'radial-gradient(circle at 30% 50%, #60a5fa 0%, transparent 50%), radial-gradient(circle at 70% 50%, #3b82f6 0%, transparent 50%)' }} />
        <div className="relative max-w-md text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-white/10 backdrop-blur mb-6">
            <Shield className="w-8 h-8 text-blue-300" strokeWidth={2} />
          </div>
          <h2 className="text-3xl font-bold text-white mb-4 tracking-tight">Start your journey with ClaimAssist</h2>
          <p className="text-slate-300 leading-relaxed mb-8">
            Create a free account and get sample policies instantly. File claims, track progress, and experience insurance the modern way.
          </p>
          <div className="space-y-3 text-left">
            {['Free account with sample policies', 'File claims in under 5 minutes', 'AI assistant available 24/7', 'Track claims in real-time'].map((item) => (
              <div key={item} className="flex items-center gap-3 text-sm text-slate-200">
                <div className="w-5 h-5 rounded-full bg-green-500/20 flex items-center justify-center flex-shrink-0">
                  <Check className="w-3 h-3 text-green-400" />
                </div>
                {item}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right side - form */}
      <div className="flex-1 flex items-center justify-center px-4 py-12 bg-white">
        <div className="w-full max-w-sm">
          <button onClick={() => navigate('/')} className="flex items-center gap-2.5 mb-8">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-blue-700 flex items-center justify-center shadow-sm shadow-blue-600/30">
              <Shield className="w-5 h-5 text-white" strokeWidth={2.5} />
            </div>
            <span className="text-xl font-bold text-slate-900 tracking-tight">ClaimAssist</span>
          </button>

          <h1 className="text-2xl font-bold text-slate-900 mb-2">Create your account</h1>
          <p className="text-sm text-slate-500 mb-6">Get started with free sample policies.</p>

          {error && (
            <div className="flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-xl mb-5 animate-slide-in">
              <AlertCircle className="w-4 h-4 text-red-500 flex-shrink-0" />
              <p className="text-sm text-red-700">{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <Field label="Full Name" required>
              <div className="relative">
                <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <Input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Jane Doe"
                  className="pl-10"
                />
              </div>
            </Field>
            <Field label="Email" required>
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <Input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  className="pl-10"
                />
              </div>
            </Field>
            <Field label="Password" required hint="At least 6 characters with a letter and number">
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <Input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="pl-10"
                />
              </div>
            </Field>

            {/* Password strength */}
            {password.length > 0 && (
              <div className="space-y-1.5 animate-fade-in">
                {passwordChecks.map((check) => (
                  <div key={check.label} className="flex items-center gap-2 text-xs">
                    <div className={`w-4 h-4 rounded-full flex items-center justify-center ${check.valid ? 'bg-green-100' : 'bg-slate-100'}`}>
                      {check.valid && <Check className="w-3 h-3 text-green-600" />}
                    </div>
                    <span className={check.valid ? 'text-green-600' : 'text-slate-400'}>{check.label}</span>
                  </div>
                ))}
              </div>
            )}

            <Button type="submit" size="lg" loading={loading} className="w-full">
              Create Account <ArrowRight className="w-4 h-4" />
            </Button>
          </form>

          <p className="text-sm text-slate-500 text-center mt-6">
            Already have an account?{' '}
            <button onClick={() => navigate('/login')} className="font-semibold text-blue-600 hover:text-blue-700 transition-colors">
              Sign in
            </button>
          </p>
        </div>
      </div>
    </div>
  );
}
