import { useState, useEffect } from 'react';
import { User, Phone, Shield, Save, CheckCircle2, AlertCircle, FileText, FolderOpen } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Field, Input } from '@/components/ui/Field';
import { Spinner } from '@/components/ui/Spinner';
import { useAuth } from '@/context/AuthContext';
import * as api from '@/lib/api';
import { navigate } from '@/lib/router';

export function ProfilePage() {
  const { customerId, fullName, signOut, refreshProfile } = useAuth();
  const [name, setName] = useState(fullName || '');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Sync name with auth context fullName when it changes
  useEffect(() => {
    if (fullName) {
      setName(fullName);
    }
  }, [fullName]);

  if (!customerId) {
    return <div className="min-h-[60vh] flex items-center justify-center"><Spinner size="lg" /></div>;
  }

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);

    try {
      await api.updateCustomer(customerId || '', name);
      setSaved(true);
      // Refresh auth context to get updated fullName from localStorage
      refreshProfile();
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update profile');
    } finally {
      setSaving(false);
    }
  };

  const handleSignOut = async () => {
    await signOut();
    // signOut already handles navigation to /login
  };

  const initials = (fullName || 'U')[0].toUpperCase();

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight mb-2">Your Profile</h1>
      <p className="text-slate-500 mb-8">Manage your account information and preferences.</p>

      {/* Profile card */}
      <Card className="overflow-hidden mb-6">
        <div className="bg-gradient-to-br from-blue-600 to-navy-800 p-6">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-2xl bg-white/20 flex items-center justify-center text-white text-2xl font-bold">
              {initials}
            </div>
            <div>
              <h2 className="text-xl font-bold text-white">{fullName || 'Account Holder'}</h2>
              <p className="text-white/80 text-sm">Customer ID: {customerId}</p>
            </div>
          </div>
        </div>
        <div className="p-6">
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
            <div className="text-center p-4 rounded-xl bg-slate-50">
              <FolderOpen className="w-5 h-5 text-blue-500 mx-auto mb-2" />
              <p className="text-xs text-slate-400">Account Status</p>
              <p className="text-sm font-bold text-green-600">Active</p>
            </div>
            <div className="text-center p-4 rounded-xl bg-slate-50">
              <Shield className="w-5 h-5 text-green-500 mx-auto mb-2" />
              <p className="text-xs text-slate-400">Plan</p>
              <p className="text-sm font-bold text-slate-900">Standard</p>
            </div>
            <div className="text-center p-4 rounded-xl bg-slate-50 col-span-2 sm:col-span-1">
              <FileText className="w-5 h-5 text-navy-500 mx-auto mb-2" />
              <p className="text-xs text-slate-400">Member Since</p>
              <p className="text-sm font-bold text-slate-900">{new Date().toLocaleDateString('en-US', { month: 'short', year: 'numeric' })}</p>
            </div>
          </div>
        </div>
      </Card>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* Edit form */}
        <div className="lg:col-span-2">
          <Card className="p-6">
            <h2 className="font-bold text-slate-900 mb-5 flex items-center gap-2">
              <User className="w-5 h-5 text-slate-400" /> Personal Information
            </h2>

            {error && (
              <div className="flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-xl mb-4">
                <AlertCircle className="w-4 h-4 text-red-500 flex-shrink-0" />
                <p className="text-sm text-red-700">{error}</p>
              </div>
            )}

            {saved && (
              <div className="flex items-center gap-2 px-4 py-3 bg-green-50 border border-green-200 rounded-xl mb-4 animate-slide-in">
                <CheckCircle2 className="w-4 h-4 text-green-500 flex-shrink-0" />
                <p className="text-sm text-green-700">Profile updated successfully!</p>
              </div>
            )}

            <form onSubmit={handleSave} className="space-y-4">
              <Field label="Full Name" required>
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                  <Input value={name} onChange={(e) => setName(e.target.value)} className="pl-10" placeholder="Jane Doe" />
                </div>
              </Field>
              <Field label="Customer ID" hint="Customer ID cannot be changed">
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                  <Input value={customerId || ''} disabled className="pl-10 bg-slate-50" />
                </div>
              </Field>
              <Button type="submit" loading={saving}>
                <Save className="w-4 h-4" /> Save Changes
              </Button>
            </form>
          </Card>
        </div>

        {/* Side panel */}
        <div className="space-y-4">
          <Card className="p-6">
            <h3 className="font-bold text-slate-900 mb-3">Quick Links</h3>
            <div className="space-y-2">
              <button onClick={() => navigate('/dashboard')} className="w-full flex items-center gap-2.5 p-3 rounded-lg hover:bg-slate-50 transition-colors text-left">
                <FolderOpen className="w-4 h-4 text-slate-400" />
                <span className="text-sm font-medium text-slate-700">Dashboard</span>
              </button>
              <button onClick={() => navigate('/policies')} className="w-full flex items-center gap-2.5 p-3 rounded-lg hover:bg-slate-50 transition-colors text-left">
                <Shield className="w-4 h-4 text-slate-400" />
                <span className="text-sm font-medium text-slate-700">My Policies</span>
              </button>
              <button onClick={() => navigate('/claims-list')} className="w-full flex items-center gap-2.5 p-3 rounded-lg hover:bg-slate-50 transition-colors text-left">
                <FileText className="w-4 h-4 text-slate-400" />
                <span className="text-sm font-medium text-slate-700">My Claims</span>
              </button>
              <button onClick={() => navigate('/contact')} className="w-full flex items-center gap-2.5 p-3 rounded-lg hover:bg-slate-50 transition-colors text-left">
                <Phone className="w-4 h-4 text-slate-400" />
                <span className="text-sm font-medium text-slate-700">Contact Support</span>
              </button>
            </div>
          </Card>

          <Card className="p-6 border-red-200">
            <h3 className="font-bold text-slate-900 mb-3">Session</h3>
            <Button variant="danger" className="w-full" onClick={handleSignOut}>
              Sign Out
            </Button>
          </Card>
        </div>
      </div>
    </div>
  );
}
