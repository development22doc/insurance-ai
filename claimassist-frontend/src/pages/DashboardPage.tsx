import { useEffect, useState } from 'react';
import { FolderOpen, FileText, Bot, ArrowRight, Plus, TrendingUp, Shield, Clock, AlertCircle } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge, statusTone } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { useAuth } from '@/context/AuthContext';
import * as api from '@/lib/api';
import type { Policy, Claim } from '@/lib/api';
import { navigate } from '@/lib/router';

export function DashboardPage() {
  const { fullName, customerId } = useAuth();
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [claims, setClaims] = useState<Claim[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadData = async () => {
      console.log('[Dashboard] Loading data, customerId:', customerId);
      if (!customerId) {
        console.log('[Dashboard] No customerId, skipping load');
        setLoading(false);
        return;
      }
      try {
        console.log('[Dashboard] Calling getAllPolicies and getAllClaims');
        const [policiesData, claimsData] = await Promise.all([
          api.getAllPolicies(),
          api.getAllClaims(),
        ]);
        console.log('[Dashboard] API calls completed:', { policiesCount: policiesData.length, claimsCount: claimsData.length });
        setPolicies(policiesData);
        setClaims(claimsData);
      } catch (err) {
        console.error('[Dashboard] Error loading data:', err);
        setError(err instanceof Error ? err.message : 'Failed to load data');
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [customerId]);

  if (loading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <Card className="p-8 text-center max-w-md">
          <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
          <h2 className="text-lg font-bold text-slate-900 mb-2">Error loading data</h2>
          <p className="text-sm text-slate-500 mb-4">{error}</p>
          <Button onClick={() => window.location.reload()}>Retry</Button>
        </Card>
      </div>
    );
  }

  const activePolicies = policies.filter(p => p.status === 'Active').length;
  const activeClaims = claims.filter(c => !['APPROVED', 'DENIED', 'PAID', 'CLOSED'].includes(c.status)).length;
  const totalCoverage = policies.reduce((sum, p) => sum + p.coverageAmount, 0);

  const stats = [
    { label: 'Active Policies', value: activePolicies, icon: FolderOpen, tone: 'bg-blue-50 text-blue-600' },
    { label: 'Open Claims', value: activeClaims, icon: FileText, tone: 'bg-amber-50 text-amber-600' },
    { label: 'Total Coverage', value: `$${(totalCoverage / 1000).toFixed(0)}K`, icon: Shield, tone: 'bg-green-50 text-green-600' },
    { label: 'Claims Processed', value: claims.length, icon: TrendingUp, tone: 'bg-navy-50 text-navy-700' },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      {/* Welcome */}
      <div className="mb-8">
        <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">
          Welcome back, {fullName?.split(' ')[0] || 'there'}
        </h1>
        <p className="text-slate-500 mt-1">Here's an overview of your insurance and claims.</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {stats.map((stat) => (
          <Card key={stat.label} className="p-5">
            <div className={`inline-flex items-center justify-center w-10 h-10 rounded-xl ${stat.tone} mb-3`}>
              <stat.icon className="w-5 h-5" />
            </div>
            <p className="text-2xl font-bold text-slate-900">{stat.value}</p>
            <p className="text-xs text-slate-500 mt-0.5">{stat.label}</p>
          </Card>
        ))}
      </div>

      {/* Quick actions */}
      <div className="grid sm:grid-cols-3 gap-4 mb-8">
        <Card hover className="p-5 group" onClick={() => navigate('/file-claim')}>
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-center group-hover:scale-110 transition-transform">
              <Plus className="w-5 h-5 text-blue-600" />
            </div>
            <div>
              <p className="font-semibold text-slate-900 text-sm">File a New Claim</p>
              <p className="text-xs text-slate-500">Start a guided claim</p>
            </div>
          </div>
        </Card>
        <Card hover className="p-5 group" onClick={() => navigate('/policies')}>
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-navy-50 border border-navy-100 flex items-center justify-center group-hover:scale-110 transition-transform">
              <FolderOpen className="w-5 h-5 text-navy-700" />
            </div>
            <div>
              <p className="font-semibold text-slate-900 text-sm">View Policies</p>
              <p className="text-xs text-slate-500">Manage your coverage</p>
            </div>
          </div>
        </Card>
        <Card hover className="p-5 group" onClick={() => navigate('/claims-list')}>
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-green-50 border border-green-100 flex items-center justify-center group-hover:scale-110 transition-transform">
              <Bot className="w-5 h-5 text-green-600" />
            </div>
            <div>
              <p className="font-semibold text-slate-900 text-sm">Track Claims</p>
              <p className="text-xs text-slate-500">View claim status</p>
            </div>
          </div>
        </Card>
      </div>

      <div className="grid lg:grid-cols-2 gap-6">
        {/* Recent policies */}
        <Card>
          <div className="p-6 border-b border-slate-100 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <FolderOpen className="w-5 h-5 text-slate-400" />
              <h2 className="font-bold text-slate-900">Your Policies</h2>
            </div>
            <button onClick={() => navigate('/policies')} className="text-sm font-semibold text-blue-600 hover:text-blue-700 flex items-center gap-1 transition-colors">
              View all <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
          <div className="p-4 space-y-2">
            {policies.length === 0 ? (
              <div className="text-center py-8 text-sm text-slate-400">No policies yet.</div>
            ) : (
              policies.slice(0, 3).map((policy) => (
                <button
                  key={policy.id}
                  onClick={() => navigate(`/policy/${policy.id}`)}
                  className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors text-left"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-lg bg-blue-50 flex items-center justify-center">
                      <Shield className="w-5 h-5 text-blue-600" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-slate-900">{policy.productType} Insurance</p>
                      <p className="text-xs text-slate-500">{policy.policyNumber}</p>
                    </div>
                  </div>
                  <Badge tone={statusTone(policy.status)} dot>{policy.status}</Badge>
                </button>
              ))
            )}
          </div>
        </Card>

        {/* Recent claims */}
        <Card>
          <div className="p-6 border-b border-slate-100 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <FileText className="w-5 h-5 text-slate-400" />
              <h2 className="font-bold text-slate-900">Recent Claims</h2>
            </div>
            <button onClick={() => navigate('/claims-list')} className="text-sm font-semibold text-blue-600 hover:text-blue-700 flex items-center gap-1 transition-colors">
              View all <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
          <div className="p-4 space-y-2">
            {claims.length === 0 ? (
              <div className="text-center py-8">
                <FileText className="w-10 h-10 text-slate-300 mx-auto mb-2" />
                <p className="text-sm text-slate-400 mb-3">No claims filed yet.</p>
                <Button size="sm" onClick={() => navigate('/file-claim')}>File Your First Claim</Button>
              </div>
            ) : (
              claims.slice(0, 3).map((claim) => (
                <button
                  key={claim.id}
                  onClick={() => navigate(`/claim/${claim.id}`)}
                  className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors text-left"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-lg bg-amber-50 flex items-center justify-center">
                      <Clock className="w-5 h-5 text-amber-600" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-slate-900">{claim.incidentType}</p>
                      <p className="text-xs text-slate-500">{claim.claimNumber}</p>
                    </div>
                  </div>
                  <Badge tone={statusTone(claim.status)} dot>{claim.status}</Badge>
                </button>
              ))
            )}
          </div>
        </Card>
      </div>

      {/* AI Assistant promo */}
      <Card className="mt-6 p-6 bg-gradient-to-r from-navy-900 to-navy-800 border-0">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-white/10 flex items-center justify-center flex-shrink-0">
            <Bot className="w-6 h-6 text-blue-300" />
          </div>
          <div className="flex-1">
            <h3 className="font-bold text-white">AI Assistant Available</h3>
            <p className="text-sm text-slate-400">Get instant answers about any of your claims directly in the claim detail page.</p>
          </div>
          <Button variant="secondary" size="sm" onClick={() => navigate('/claims-list')} className="hidden sm:flex">
            View Claims
          </Button>
        </div>
      </Card>
    </div>
  );
}
