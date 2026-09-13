import { useEffect, useState } from 'react';
import { Shield, ArrowLeft, Calendar, DollarSign, FileText, Plus, CheckCircle2 } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge, statusTone } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import * as api from '@/lib/api';
import type { Policy, Claim } from '@/lib/api';
import { navigate } from '@/lib/router';

export function PolicyDetailPage({ policyId }: { policyId: string }) {
  const [policy, setPolicy] = useState<Policy | null>(null);
  const [claims, setClaims] = useState<Claim[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [policyData, claimsData] = await Promise.all([
          api.getPolicy(policyId),
          api.getAllClaims(),
        ]);
        setPolicy(policyData);
        // Filter claims for this policy
        setClaims(claimsData.filter(c => c.policyId === policyId));
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load policy');
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [policyId]);

  if (loading) {
    return <div className="min-h-[60vh] flex items-center justify-center"><Spinner size="lg" /></div>;
  }

  if (error || !policy) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <Shield className="w-12 h-12 text-slate-300 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-slate-900 mb-2">Policy not found</h2>
        <Button variant="secondary" onClick={() => navigate('/policies')} className="mt-4">Back to Policies</Button>
      </div>
    );
  }

  const coverageDetails = [
    { label: 'Coverage Limit', value: `$${policy.coverageAmount.toLocaleString()}` },
    { label: 'Annual Premium', value: `$${policy.premium.toLocaleString()}` },
    { label: 'Policy Type', value: policy.planName },
  ];

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      <button onClick={() => navigate('/policies')} className="flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-blue-600 transition-colors mb-6">
        <ArrowLeft className="w-4 h-4" /> Back to Policies
      </button>

      {/* Policy header card */}
      <Card className="overflow-hidden mb-6">
        <div className="bg-gradient-to-br from-blue-600 to-navy-800 p-8">
          <div className="flex items-start justify-between flex-wrap gap-4">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-2xl bg-white/20 flex items-center justify-center">
                <Shield className="w-7 h-7 text-white" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-white">{policy.productType} Insurance</h1>
                <p className="text-white/80 mt-0.5">{policy.planName}</p>
                <p className="text-sm text-white/60 mt-1 font-mono">{policy.policyNumber}</p>
              </div>
            </div>
            <Badge tone={statusTone(policy.status)} className="bg-white/20 text-white border-white/30 text-sm px-3 py-1.5">
              {policy.status}
            </Badge>
          </div>
        </div>
        <div className="p-6">
          <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
            {coverageDetails.map((detail) => (
              <div key={detail.label} className="text-center p-4 rounded-xl bg-slate-50">
                <p className="text-xs text-slate-500 uppercase tracking-wide">{detail.label}</p>
                <p className="text-lg font-bold text-slate-900 mt-1">{detail.value}</p>
              </div>
            ))}
          </div>
        </div>
      </Card>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* Dates & Coverage */}
        <div className="lg:col-span-2 space-y-6">
          <Card className="p-6">
            <h2 className="font-bold text-slate-900 mb-4 flex items-center gap-2">
              <Calendar className="w-5 h-5 text-slate-400" /> Policy Timeline
            </h2>
            <div className="space-y-4">
              <div className="flex items-center gap-4">
                <div className="w-10 h-10 rounded-full bg-green-100 border-2 border-green-300 flex items-center justify-center flex-shrink-0">
                  <CheckCircle2 className="w-5 h-5 text-green-600" />
                </div>
                <div className="flex-1">
                  <p className="text-sm font-semibold text-slate-900">Effective Date</p>
                  <p className="text-xs text-slate-500">{new Date(policy.effectiveDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</p>
                </div>
              </div>
              <div className="flex items-center gap-4">
                <div className="w-10 h-10 rounded-full bg-blue-100 border-2 border-blue-300 flex items-center justify-center flex-shrink-0">
                  <Calendar className="w-5 h-5 text-blue-600" />
                </div>
                <div className="flex-1">
                  <p className="text-sm font-semibold text-slate-900">Renewal Date</p>
                  <p className="text-xs text-slate-500">{new Date(policy.renewalDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</p>
                </div>
              </div>
            </div>
          </Card>

          {/* Claims under this policy */}
          <Card>
            <div className="p-6 border-b border-slate-100 flex items-center justify-between">
              <h2 className="font-bold text-slate-900 flex items-center gap-2">
                <FileText className="w-5 h-5 text-slate-400" /> Claims Under This Policy
              </h2>
              <Button size="sm" onClick={() => navigate('/file-claim')}>
                <Plus className="w-4 h-4" /> File Claim
              </Button>
            </div>
            <div className="p-4">
              {claims.length === 0 ? (
                <div className="text-center py-8">
                  <FileText className="w-10 h-10 text-slate-300 mx-auto mb-2" />
                  <p className="text-sm text-slate-400">No claims filed under this policy.</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {claims.map((claim) => (
                    <button
                      key={claim.id}
                      onClick={() => navigate(`/claim/${claim.id}`)}
                      className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-colors text-left"
                    >
                      <div>
                        <p className="text-sm font-semibold text-slate-900">{claim.incidentType}</p>
                        <p className="text-xs text-slate-500">{claim.claimNumber} · ${claim.estimatedAmountCents ? (claim.estimatedAmountCents / 100).toLocaleString() : 'N/A'}</p>
                      </div>
                      <Badge tone={statusTone(claim.status)} dot>{claim.status}</Badge>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </Card>
        </div>

        {/* Quick actions */}
        <div className="space-y-6">
          <Card className="p-6">
            <h3 className="font-bold text-slate-900 mb-4">Quick Actions</h3>
            <div className="space-y-2">
              <Button variant="secondary" className="w-full justify-start" onClick={() => navigate('/file-claim')}>
                <FileText className="w-4 h-4" /> File a Claim
              </Button>
              <Button variant="ghost" className="w-full justify-start" onClick={() => navigate('/contact')}>
                <DollarSign className="w-4 h-4" /> Contact Support
              </Button>
            </div>
          </Card>

          <Card className="p-6 bg-gradient-to-br from-blue-50 to-navy-50 border-blue-100">
            <Shield className="w-8 h-8 text-blue-600 mb-3" />
            <h3 className="font-bold text-slate-900 mb-1">You're Protected</h3>
            <p className="text-sm text-slate-500">Your policy is active and covers up to ${policy.coverageAmount.toLocaleString()} in damages.</p>
          </Card>
        </div>
      </div>
    </div>
  );
}
