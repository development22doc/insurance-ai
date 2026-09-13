import { useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, Check, Shield, CheckCircle2, AlertCircle, Car, Home, Heart, Plane, Umbrella } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Field, Input, Select } from '@/components/ui/Field';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { Spinner } from '@/components/ui/Spinner';
import * as api from '@/lib/api';
import type { Policy } from '@/lib/api';
import { navigate } from '@/lib/router';

const STEPS = ['Select Policy', 'Incident Details', 'Date & Amount', 'Review', 'Confirmation'];
const INCIDENT_TYPES = ['Collision', 'Theft', 'Fire Damage', 'Water Damage', 'Vandalism', 'Natural Disaster', 'Medical Emergency', 'Trip Cancellation', 'Lost Baggage', 'Other'];

export function ClaimSubmissionPage() {
  const [step, setStep] = useState(0);
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [loadingPolicies, setLoadingPolicies] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createdClaimId, setCreatedClaimId] = useState<string | null>(null);

  const [form, setForm] = useState({
    policyId: '',
    incidentType: '',
    incidentDate: '',
    estimatedAmountCents: '',
  });

  useEffect(() => {
    const loadPolicies = async () => {
      try {
        const data = await api.getAllPolicies();
        setPolicies(data.filter(p => p.status === 'Active'));
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load policies');
      } finally {
        setLoadingPolicies(false);
      }
    };

    loadPolicies();
  }, []);

  const selectedPolicy = policies.find(p => p.id === form.policyId);

  const validateStep = (): string | null => {
    if (step === 0 && !form.policyId) return 'Please select a policy.';
    if (step === 1) {
      if (!form.incidentType) return 'Please select an incident type.';
    }
    if (step === 2) {
      if (!form.incidentDate) return 'Please select the incident date.';
      if (new Date(form.incidentDate) > new Date()) return 'Incident date cannot be in the future.';
      if (!form.estimatedAmountCents || Number(form.estimatedAmountCents) <= 0) return 'Please enter a valid estimated amount.';
    }
    return null;
  };

  const next = () => {
    const err = validateStep();
    if (err) { setError(err); return; }
    setError(null);
    setStep(s => Math.min(s + 1, STEPS.length - 1));
  };

  const back = () => {
    setError(null);
    setStep(s => Math.max(s - 1, 0));
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    setError(null);

    try {
      const claim = await api.createClaim(
        form.policyId,
        form.incidentType,
        form.incidentDate,
        form.estimatedAmountCents ? Number(form.estimatedAmountCents) * 100 : undefined
      );
      setCreatedClaimId(claim.id);
      setStep(4);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to submit claim. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loadingPolicies) {
    return <div className="min-h-[60vh] flex items-center justify-center"><Spinner size="lg" /></div>;
  }

  if (policies.length === 0) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center animate-fade-in">
        <Shield className="w-12 h-12 text-slate-300 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-slate-900 mb-2">No active policies</h2>
        <p className="text-sm text-slate-500 mb-6">You need an active policy before filing a claim.</p>
        <Button onClick={() => navigate('/policies')}>View Policies</Button>
      </div>
    );
  }

  const productIcon = (type: string) => {
    const map: Record<string, typeof Car> = { Auto: Car, Home, Health: Heart, Travel: Plane, Life: Umbrella };
    return map[type] || Shield;
  };

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      <button onClick={() => navigate('/claims-list')} className="flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-blue-600 transition-colors mb-6">
        <ArrowLeft className="w-4 h-4" /> Back to Claims
      </button>

      <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight mb-2">File a Claim</h1>
      <p className="text-slate-500 mb-6">Follow the steps below to submit your claim.</p>

      {step < 4 && <ProgressBar current={step + 1} total={4} />}

      {/* Step indicators */}
      {step < 4 && (
        <div className="flex items-center justify-between mt-4 mb-8">
          {STEPS.slice(0, 4).map((label, idx) => (
            <div key={label} className="flex items-center">
              <div className={`flex items-center gap-2 ${idx > step ? 'opacity-40' : ''}`}>
                <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-colors ${
                  idx < step ? 'bg-green-500 text-white' : idx === step ? 'bg-blue-600 text-white' : 'bg-slate-200 text-slate-500'
                }`}>
                  {idx < step ? <Check className="w-4 h-4" /> : idx + 1}
                </div>
                <span className={`text-xs font-medium hidden sm:block ${idx === step ? 'text-slate-900' : 'text-slate-400'}`}>{label}</span>
              </div>
              {idx < 3 && <div className={`w-8 sm:w-12 h-0.5 mx-1 sm:mx-2 ${idx < step ? 'bg-green-300' : 'bg-slate-200'}`} />}
            </div>
          ))}
        </div>
      )}

      {error && (
        <div className="flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-xl mb-5 animate-slide-in">
          <AlertCircle className="w-4 h-4 text-red-500 flex-shrink-0" />
          <p className="text-sm text-red-700">{error}</p>
        </div>
      )}

      {/* Step 0: Select Policy */}
      {step === 0 && (
        <Card className="p-6 animate-slide-in">
          <h2 className="font-bold text-slate-900 mb-1">Select a Policy</h2>
          <p className="text-sm text-slate-500 mb-5">Choose the policy this claim relates to.</p>
          <div className="space-y-3">
            {policies.map((policy) => {
              const Icon = productIcon(policy.productType);
              const selected = form.policyId === policy.id;
              return (
                <button
                  key={policy.id}
                  onClick={() => setForm({ ...form, policyId: policy.id })}
                  className={`w-full flex items-center gap-4 p-4 rounded-xl border-2 transition-all text-left ${
                    selected ? 'border-blue-500 bg-blue-50' : 'border-slate-200 hover:border-blue-300 hover:bg-slate-50'
                  }`}
                >
                  <div className={`w-11 h-11 rounded-xl flex items-center justify-center flex-shrink-0 ${selected ? 'bg-blue-600' : 'bg-blue-50'}`}>
                    <Icon className={`w-5 h-5 ${selected ? 'text-white' : 'text-blue-600'}`} />
                  </div>
                  <div className="flex-1">
                    <p className="font-semibold text-slate-900">{policy.productType} Insurance</p>
                    <p className="text-xs text-slate-500 font-mono">{policy.policyNumber}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-slate-400">Coverage</p>
                    <p className="text-sm font-bold text-slate-900">${policy.coverageAmount.toLocaleString()}</p>
                  </div>
                  {selected && <CheckCircle2 className="w-5 h-5 text-blue-600 flex-shrink-0" />}
                </button>
              );
            })}
          </div>
        </Card>
      )}

      {/* Step 1: Incident Details */}
      {step === 1 && (
        <Card className="p-6 animate-slide-in space-y-5">
          <div>
            <h2 className="font-bold text-slate-900 mb-1">Incident Details</h2>
            <p className="text-sm text-slate-500 mb-5">Tell us what happened.</p>
          </div>
          <Field label="Incident Type" required>
            <Select value={form.incidentType} onChange={(e) => setForm({ ...form, incidentType: e.target.value })}>
              <option value="">Select an incident type...</option>
              {INCIDENT_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </Select>
          </Field>
        </Card>
      )}

      {/* Step 2: Date & Amount */}
      {step === 2 && (
        <Card className="p-6 animate-slide-in space-y-5">
          <div>
            <h2 className="font-bold text-slate-900 mb-1">Date & Estimated Amount</h2>
            <p className="text-sm text-slate-500 mb-5">When did the incident occur and how much do you estimate?</p>
          </div>
          <Field label="Incident Date" required hint="The date the incident occurred">
            <Input type="date" value={form.incidentDate} max={new Date().toISOString().split('T')[0]} onChange={(e) => setForm({ ...form, incidentDate: e.target.value })} />
          </Field>
          <Field label="Estimated Amount (USD)" required hint="Your best estimate of the total cost">
            <Input type="number" min="0" step="100" value={form.estimatedAmountCents} onChange={(e) => setForm({ ...form, estimatedAmountCents: e.target.value })} placeholder="5000" />
          </Field>
          {selectedPolicy && Number(form.estimatedAmountCents) > selectedPolicy.coverageAmount && (
            <div className="flex items-center gap-2 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl">
              <AlertCircle className="w-4 h-4 text-amber-500 flex-shrink-0" />
              <p className="text-sm text-amber-700">This amount exceeds your coverage limit of ${selectedPolicy.coverageAmount.toLocaleString()}.</p>
            </div>
          )}
        </Card>
      )}

      {/* Step 3: Review */}
      {step === 3 && (
        <Card className="p-6 animate-slide-in">
          <h2 className="font-bold text-slate-900 mb-1">Review Your Claim</h2>
          <p className="text-sm text-slate-500 mb-5">Please review all details before submitting.</p>
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-slate-50">
              <p className="text-xs text-slate-400 uppercase tracking-wide mb-1">Policy</p>
              <p className="font-semibold text-slate-900">{selectedPolicy?.productType} Insurance</p>
              <p className="text-sm text-slate-500 font-mono">{selectedPolicy?.policyNumber}</p>
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <div className="p-4 rounded-xl bg-slate-50">
                <p className="text-xs text-slate-400 uppercase tracking-wide mb-1">Incident Type</p>
                <p className="font-semibold text-slate-900">{form.incidentType}</p>
              </div>
              <div className="p-4 rounded-xl bg-slate-50">
                <p className="text-xs text-slate-400 uppercase tracking-wide mb-1">Incident Date</p>
                <p className="font-semibold text-slate-900">{new Date(form.incidentDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</p>
              </div>
            </div>
            <div className="p-4 rounded-xl bg-blue-50 border border-blue-100">
              <p className="text-xs text-blue-400 uppercase tracking-wide mb-1">Estimated Amount</p>
              <p className="text-2xl font-bold text-blue-700">${Number(form.estimatedAmountCents).toLocaleString()}</p>
            </div>
          </div>
        </Card>
      )}

      {/* Step 4: Confirmation */}
      {step === 4 && (
        <Card className="p-8 text-center animate-slide-in">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-green-50 border-2 border-green-200 mb-4">
            <CheckCircle2 className="w-8 h-8 text-green-600" />
          </div>
          <h2 className="text-xl font-bold text-slate-900 mb-2">Claim Submitted Successfully!</h2>
          <p className="text-sm text-slate-500 mb-6">Your claim has been received and is now being processed. You can track its progress in your claims dashboard.</p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Button onClick={() => navigate(createdClaimId ? `/claim/${createdClaimId}` : '/claims-list')}>
              Track This Claim <ArrowRight className="w-4 h-4" />
            </Button>
            <Button variant="secondary" onClick={() => navigate('/dashboard')}>Go to Dashboard</Button>
          </div>
        </Card>
      )}

      {/* Navigation buttons */}
      {step < 4 && (
        <div className="flex justify-between mt-6">
          <Button variant="ghost" onClick={back} disabled={step === 0}>
            <ArrowLeft className="w-4 h-4" /> Back
          </Button>
          {step < 3 ? (
            <Button onClick={next}>
              Continue <ArrowRight className="w-4 h-4" />
            </Button>
          ) : (
            <Button onClick={handleSubmit} loading={submitting} variant="success">
              {submitting ? 'Submitting...' : 'Submit Claim'} <Check className="w-4 h-4" />
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
