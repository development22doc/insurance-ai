import { FileText, ArrowRight, Clock, Bot, CheckCircle2, ListChecks } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ClaimsIllustration } from '@/components/shared/Illustrations';
import { navigate } from '@/lib/router';
import { useAuth } from '@/context/AuthContext';

export function ClaimsPage() {
  const { isAuthenticated } = useAuth();

  const steps = [
    { icon: ListChecks, title: 'Select Policy', desc: 'Choose the policy your claim relates to.' },
    { icon: FileText, title: 'Incident Details', desc: 'Describe what happened and the type of incident.' },
    { icon: Clock, title: 'Date & Amount', desc: 'When did it happen and what is the estimated cost?' },
    { icon: CheckCircle2, title: 'Review & Submit', desc: 'Confirm your details and submit your claim.' },
  ];

  return (
    <div className="animate-fade-in">
      {/* Hero */}
      <section className="relative bg-gradient-to-b from-blue-50/50 to-white py-16 lg:py-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div>
              <Badge tone="blue" className="mb-4">File a Claim</Badge>
              <h1 className="text-4xl sm:text-5xl font-bold text-slate-900 tracking-tight mb-4">
                Claims that don't feel like work
              </h1>
              <p className="text-lg text-slate-600 mb-8 max-w-lg">
                Our guided claim process takes you step by step through filing your claim. No paperwork, no waiting on hold — just a simple, transparent journey from start to payout.
              </p>
              <div className="flex flex-col sm:flex-row gap-3">
                <Button size="lg" onClick={() => navigate(isAuthenticated ? '/file-claim' : '/register')}>
                  {isAuthenticated ? 'Start a Claim' : 'Sign Up to File'} <ArrowRight className="w-5 h-5" />
                </Button>
                <Button size="lg" variant="secondary" onClick={() => navigate(isAuthenticated ? '/claims-list' : '/login')}>
                  {isAuthenticated ? 'View My Claims' : 'Sign In'}
                </Button>
              </div>
            </div>
            <div className="flex justify-center lg:justify-end">
              <div className="relative w-full max-w-sm">
                <div className="absolute inset-0 bg-gradient-to-br from-blue-100/40 to-navy-100/30 rounded-3xl blur-2xl" />
                <ClaimsIllustration className="relative w-full h-auto" />
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="py-16 lg:py-20 bg-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-12">
            <Badge tone="blue" className="mb-3">How It Works</Badge>
            <h2 className="text-3xl font-bold text-slate-900 tracking-tight">Four simple steps to file your claim</h2>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {steps.map((step, idx) => (
              <Card key={step.title} className="p-6 relative">
                <div className="absolute top-4 right-4 text-5xl font-bold text-slate-100 select-none">
                  {String(idx + 1).padStart(2, '0')}
                </div>
                <div className="relative">
                  <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-blue-50 border border-blue-100 mb-4">
                    <step.icon className="w-6 h-6 text-blue-600" />
                  </div>
                  <h3 className="text-lg font-semibold text-slate-900 mb-1.5">{step.title}</h3>
                  <p className="text-sm text-slate-500">{step.desc}</p>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* Claim statuses */}
      <section className="py-16 bg-slate-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div>
              <Badge tone="blue" className="mb-3">Track Everything</Badge>
              <h2 className="text-3xl font-bold text-slate-900 tracking-tight mb-4">
                Watch your claim move through every stage
              </h2>
              <p className="text-slate-600 mb-6">
                From the moment you submit, you'll see exactly where your claim stands. Our visual timeline keeps you informed every step of the way.
              </p>
              <div className="space-y-3">
                {[
                  { label: 'Submitted', desc: 'Your claim is received and logged.', tone: 'bg-blue-500' },
                  { label: 'Under Review', desc: 'An adjuster is evaluating your claim.', tone: 'bg-amber-500' },
                  { label: 'Approved', desc: 'Your claim has been approved for payout.', tone: 'bg-green-500' },
                  { label: 'Payout', desc: 'Funds are on their way to your account.', tone: 'bg-green-600' },
                ].map((stage) => (
                  <div key={stage.label} className="flex items-center gap-3">
                    <div className={`w-3 h-3 rounded-full ${stage.tone}`} />
                    <div>
                      <span className="text-sm font-semibold text-slate-900">{stage.label}</span>
                      <span className="text-sm text-slate-500 ml-2">{stage.desc}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <Card className="p-8">
              <div className="flex items-center gap-3 mb-6">
                <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-navy-700 flex items-center justify-center">
                  <Bot className="w-5 h-5 text-white" />
                </div>
                <div>
                  <p className="font-semibold text-slate-900">AI Assistant</p>
                  <p className="text-xs text-slate-500">Available on every claim</p>
                </div>
              </div>
              <p className="text-sm text-slate-600 mb-4">
                Each claim detail page includes an AI assistant that can answer questions about your specific claim, explain next steps, and help you understand the process.
              </p>
              <div className="space-y-2.5">
                {[
                  'What is the status of my claim?',
                  'How long will the review take?',
                  'What documents do I need?',
                  'What is my coverage amount?',
                ].map((q) => (
                  <div key={q} className="flex items-center gap-2 text-sm text-slate-600 bg-slate-50 rounded-lg px-3 py-2">
                    <div className="w-1.5 h-1.5 rounded-full bg-blue-400" />
                    {q}
                  </div>
                ))}
              </div>
            </Card>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-16 bg-navy-900">
        <div className="max-w-3xl mx-auto px-4 text-center">
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-3">Ready to file your claim?</h2>
          <p className="text-slate-300 mb-6">It takes less than 5 minutes. Sign in or create an account to get started.</p>
          <Button size="lg" onClick={() => navigate(isAuthenticated ? '/file-claim' : '/register')}>
            {isAuthenticated ? 'Start Filing' : 'Get Started'} <ArrowRight className="w-5 h-5" />
          </Button>
        </div>
      </section>
    </div>
  );
}
