import { useEffect, useState } from 'react';
import { FileText, Plus, ArrowRight, Filter } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge, statusTone } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import * as api from '@/lib/api';
import type { Claim } from '@/lib/api';
import { navigate } from '@/lib/router';
import { CLAIM_STATUSES } from '@/lib/types';

export function ClaimsListPage() {
  const [claims, setClaims] = useState<Claim[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<string>('All');

  useEffect(() => {
    const loadClaims = async () => {
      try {
        const data = await api.getAllClaims();
        setClaims(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load claims');
      } finally {
        setLoading(false);
      }
    };

    loadClaims();
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

  const filtered = filter === 'All' ? claims : claims.filter(c => c.status === filter);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      <div className="flex items-center justify-between mb-8 flex-wrap gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">Your Claims</h1>
          <p className="text-slate-500 mt-1">Track and manage all your insurance claims.</p>
        </div>
        <Button onClick={() => navigate('/file-claim')}>
          <Plus className="w-4 h-4" /> File New Claim
        </Button>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-2 mb-6 overflow-x-auto pb-1">
        <Filter className="w-4 h-4 text-slate-400 flex-shrink-0" />
        {['All', ...CLAIM_STATUSES].map((status) => (
          <button
            key={status}
            onClick={() => setFilter(status)}
            className={`px-3 py-1.5 text-sm font-medium rounded-lg whitespace-nowrap transition-colors ${
              filter === status
                ? 'bg-blue-600 text-white'
                : 'bg-white text-slate-600 border border-slate-200 hover:border-blue-300 hover:text-blue-600'
            }`}
          >
            {status}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <Card className="p-12 text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-50 border border-blue-100 mb-4">
            <FileText className="w-8 h-8 text-blue-500" />
          </div>
          <h3 className="text-lg font-bold text-slate-900 mb-2">
            {filter === 'All' ? 'No claims yet' : `No ${filter} claims`}
          </h3>
          <p className="text-sm text-slate-500 mb-6">
            {filter === 'All' ? 'File your first claim to get started.' : 'Try a different filter or file a new claim.'}
          </p>
          <Button onClick={() => navigate('/file-claim')}>
            <Plus className="w-4 h-4" /> File a Claim
          </Button>
        </Card>
      ) : (
        <div className="space-y-3">
          {filtered.map((claim) => (
            <Card key={claim.id} hover onClick={() => navigate(`/claim/${claim.id}`)} className="p-5">
              <div className="flex items-center justify-between gap-4 flex-wrap">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 rounded-xl bg-blue-50 flex items-center justify-center flex-shrink-0">
                    <FileText className="w-6 h-6 text-blue-600" />
                  </div>
                  <div>
                    <p className="font-semibold text-slate-900">{claim.incidentType}</p>
                    <p className="text-xs text-slate-500 font-mono">{claim.claimNumber}</p>
                  </div>
                </div>
                <div className="flex items-center gap-4 flex-wrap">
                  <div className="text-right">
                    <p className="text-xs text-slate-400">Amount</p>
                    <p className="text-sm font-bold text-slate-900">{claim.estimatedAmountCents ? `$${(claim.estimatedAmountCents / 100).toLocaleString()}` : 'N/A'}</p>
                  </div>
                  <Badge tone={statusTone(claim.status)} dot>{claim.status}</Badge>
                  <ArrowRight className="w-4 h-4 text-slate-300" />
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
