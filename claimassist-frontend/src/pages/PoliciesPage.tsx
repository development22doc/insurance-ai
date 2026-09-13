import { useEffect, useState } from 'react';
import { Shield, Plus, ArrowRight, Calendar, DollarSign } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge, statusTone } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import * as api from '@/lib/api';
import type { Policy } from '@/lib/api';
import { navigate } from '@/lib/router';

export function PoliciesPage() {
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadPolicies = async () => {
      try {
        const data = await api.getAllPolicies();
        setPolicies(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load policies');
      } finally {
        setLoading(false);
      }
    };

    loadPolicies();
  }, []);

  if (loading) {
    return <div className="min-h-[60vh] flex items-center justify-center"><Spinner size="lg" /></div>;
  }

  if (error) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <Card className="p-8 text-center max-w-md">
          <p className="text-sm text-slate-500 mb-4">{error}</p>
          <Button onClick={() => window.location.reload()}>Retry</Button>
        </Card>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      <div className="flex items-center justify-between mb-8 flex-wrap gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">Your Policies</h1>
          <p className="text-slate-500 mt-1">Manage and review your insurance coverage.</p>
        </div>
        <Button onClick={() => navigate('/products')}>
          <Plus className="w-4 h-4" /> Browse Products
        </Button>
      </div>

      {policies.length === 0 ? (
        <Card className="p-12 text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-50 border border-blue-100 mb-4">
            <Shield className="w-8 h-8 text-blue-500" />
          </div>
          <h3 className="text-lg font-bold text-slate-900 mb-2">No policies yet</h3>
          <p className="text-sm text-slate-500 mb-6">Explore our products to find the right coverage for you.</p>
          <Button onClick={() => navigate('/products')}>Explore Products</Button>
        </Card>
      ) : (
        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-5">
          {policies.map((policy) => (
            <Card key={policy.id} hover className="overflow-hidden" onClick={() => navigate(`/policy/${policy.id}`)}>
              {/* Header */}
              <div className="bg-gradient-to-br from-blue-500 to-navy-700 p-5">
                <div className="flex items-center justify-between mb-3">
                  <div className="w-10 h-10 rounded-lg bg-white/20 flex items-center justify-center">
                    <Shield className="w-5 h-5 text-white" />
                  </div>
                  <Badge tone={statusTone(policy.status)} className="bg-white/20 text-white border-white/30">
                    {policy.status}
                  </Badge>
                </div>
                <h3 className="text-lg font-bold text-white">{policy.productType} Insurance</h3>
                <p className="text-sm text-white/80">{policy.planName}</p>
              </div>
              {/* Body */}
              <div className="p-5 space-y-3">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-500">Policy Number</span>
                  <span className="font-mono font-semibold text-slate-900">{policy.policyNumber}</span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-500 flex items-center gap-1"><DollarSign className="w-3.5 h-3.5" /> Coverage</span>
                  <span className="font-semibold text-slate-900">${policy.coverageAmount.toLocaleString()}</span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-500 flex items-center gap-1"><Calendar className="w-3.5 h-3.5" /> Renewal</span>
                  <span className="font-semibold text-slate-900">{new Date(policy.renewalDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}</span>
                </div>
                <div className="pt-3 border-t border-slate-100 flex items-center justify-between">
                  <span className="text-sm text-slate-500">Premium</span>
                  <span className="text-lg font-bold text-slate-900">${policy.premium.toLocaleString()}<span className="text-xs text-slate-400 font-normal">/yr</span></span>
                </div>
              </div>
              <div className="px-5 pb-5">
                <div className="flex items-center gap-1 text-sm font-semibold text-blue-600">
                  View Details <ArrowRight className="w-4 h-4" />
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
