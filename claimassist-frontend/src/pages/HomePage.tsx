import { Shield, ArrowRight, FileText, Bot, Clock, CheckCircle2, Car, Home, Heart, Plane, Umbrella, Users, Award } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { HeroIllustration, AIIllustration, ClaimsIllustration } from '@/components/shared/Illustrations';
import { navigate } from '@/lib/router';

export function HomePage() {
  const products = [
    { icon: Car, name: 'Auto Insurance', desc: 'Comprehensive coverage for your vehicle with instant claims.', color: 'bg-blue-50 text-blue-600' },
    { icon: Home, name: 'Home Insurance', desc: 'Protect your home and belongings against unexpected events.', color: 'bg-navy-50 text-navy-700' },
    { icon: Heart, name: 'Health Insurance', desc: 'Quality healthcare coverage tailored to your family needs.', color: 'bg-green-50 text-green-600' },
    { icon: Plane, name: 'Travel Insurance', desc: 'Travel with confidence with worldwide trip protection.', color: 'bg-blue-50 text-blue-600' },
    { icon: Umbrella, name: 'Life Insurance', desc: 'Secure your family future with flexible life coverage.', color: 'bg-navy-50 text-navy-700' },
  ];

  const features = [
    { icon: Bot, title: 'AI-Powered Assistant', desc: 'Get instant answers about your claims with our intelligent assistant available 24/7.' },
    { icon: FileText, title: 'Easy Claim Filing', desc: 'File claims in minutes with our guided multi-step submission flow.' },
    { icon: Clock, title: 'Real-Time Tracking', desc: 'Track your claim status with a visual timeline from submission to payout.' },
    { icon: Shield, title: 'Bank-Grade Security', desc: 'Your data is protected with enterprise-level encryption and security.' },
  ];

  return (
    <div className="animate-fade-in">
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-b from-blue-50/50 via-white to-white">
        <div className="absolute inset-0 bg-grid-pattern opacity-[0.03]" style={{ backgroundImage: 'linear-gradient(#1e40af 1px, transparent 1px), linear-gradient(90deg, #1e40af 1px, transparent 1px)', backgroundSize: '40px 40px' }} />
        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-16 pb-20 lg:pt-24 lg:pb-28">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div className="animate-slide-up">
              <Badge tone="blue" dot className="mb-5">
                <span className="px-1">Modern Insurance Platform</span>
              </Badge>
              <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold text-slate-900 leading-[1.1] tracking-tight">
                Insurance made{' '}
                <span className="bg-gradient-to-r from-blue-600 to-navy-800 bg-clip-text text-transparent">simple</span>
                , claims made{' '}
                <span className="bg-gradient-to-r from-navy-800 to-blue-600 bg-clip-text text-transparent">effortless</span>
              </h1>
              <p className="mt-5 text-lg text-slate-600 leading-relaxed max-w-xl">
                Manage your policies, file claims in minutes, and get AI-powered assistance throughout your entire claim journey. ClaimAssist brings modern technology to insurance.
              </p>
              <div className="mt-8 flex flex-col sm:flex-row gap-3">
                <Button size="lg" onClick={() => navigate('/register')}>
                  Get Started Free <ArrowRight className="w-5 h-5" />
                </Button>
                <Button size="lg" variant="secondary" onClick={() => navigate('/claims')}>
                  File a Claim
                </Button>
              </div>
              <div className="mt-8 flex items-center gap-6 text-sm text-slate-500">
                <div className="flex items-center gap-1.5"><CheckCircle2 className="w-4 h-4 text-green-500" /> No paperwork</div>
                <div className="flex items-center gap-1.5"><CheckCircle2 className="w-4 h-4 text-green-500" /> Instant tracking</div>
                <div className="flex items-center gap-1.5"><CheckCircle2 className="w-4 h-4 text-green-500" /> 24/7 support</div>
              </div>
            </div>
            <div className="relative flex justify-center lg:justify-end animate-slide-up">
              <div className="relative w-full max-w-md">
                <div className="absolute inset-0 bg-gradient-to-br from-blue-200/30 to-navy-200/20 rounded-3xl blur-3xl" />
                <HeroIllustration className="relative w-full h-auto" />
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Products */}
      <section className="py-20 bg-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-12">
            <Badge tone="blue" className="mb-3">Our Products</Badge>
            <h2 className="text-3xl sm:text-4xl font-bold text-slate-900 tracking-tight">Insurance for everything that matters</h2>
            <p className="mt-3 text-slate-600">Comprehensive coverage options designed for modern life, with claims that actually get processed quickly.</p>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {products.map((product) => (
              <Card key={product.name} hover className="group">
                <div className="p-6">
                  <div className={`inline-flex items-center justify-center w-12 h-12 rounded-xl ${product.color} mb-4 transition-transform group-hover:scale-110`}>
                    <product.icon className="w-6 h-6" />
                  </div>
                  <h3 className="text-lg font-semibold text-slate-900 mb-1.5">{product.name}</h3>
                  <p className="text-sm text-slate-500 leading-relaxed">{product.desc}</p>
                  <button onClick={() => navigate('/products')} className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-blue-600 hover:gap-2 transition-all">
                    Learn more <ArrowRight className="w-4 h-4" />
                  </button>
                </div>
              </Card>
            ))}
            <Card className="bg-gradient-to-br from-navy-900 to-navy-800 border-0">
              <div className="p-6 flex flex-col justify-between h-full">
                <div>
                  <Users className="w-8 h-8 text-blue-300 mb-4" />
                  <h3 className="text-lg font-semibold text-white mb-1.5">Bundle & Save</h3>
                  <p className="text-sm text-slate-400 leading-relaxed">Combine multiple policies and save up to 25% on premiums.</p>
                </div>
                <Button variant="secondary" size="sm" className="mt-4 self-start" onClick={() => navigate('/products')}>Explore Bundles</Button>
              </div>
            </Card>
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="py-20 bg-slate-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div>
              <Badge tone="blue" className="mb-3">Why ClaimAssist</Badge>
              <h2 className="text-3xl sm:text-4xl font-bold text-slate-900 tracking-tight mb-4">
                The smartest way to manage your insurance
              </h2>
              <p className="text-slate-600 mb-8">We've reimagined insurance for the digital age. No more call centers, no more paperwork, no more waiting.</p>
              <div className="space-y-5">
                {features.map((feature) => (
                  <div key={feature.title} className="flex gap-4">
                    <div className="flex-shrink-0 w-11 h-11 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-center">
                      <feature.icon className="w-5 h-5 text-blue-600" />
                    </div>
                    <div>
                      <h3 className="text-base font-semibold text-slate-900">{feature.title}</h3>
                      <p className="text-sm text-slate-500 mt-0.5 leading-relaxed">{feature.desc}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <div className="relative">
              <div className="absolute inset-0 bg-gradient-to-br from-blue-100/40 to-navy-100/30 rounded-3xl blur-2xl" />
              <Card className="relative p-8">
                <div className="flex items-center gap-3 mb-6 pb-6 border-b border-slate-100">
                  <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-navy-700 flex items-center justify-center">
                    <Bot className="w-5 h-5 text-white" />
                  </div>
                  <div>
                    <p className="font-semibold text-slate-900">ClaimAssist AI</p>
                    <p className="text-xs text-green-600 flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-green-500" /> Online now</p>
                  </div>
                </div>
                <div className="space-y-3">
                  <div className="flex justify-end">
                    <div className="bg-blue-600 text-white text-sm px-4 py-2.5 rounded-2xl rounded-br-md max-w-[80%]">
                      What's the status of my auto claim?
                    </div>
                  </div>
                  <div className="flex justify-start">
                    <div className="bg-slate-100 text-slate-700 text-sm px-4 py-2.5 rounded-2xl rounded-bl-md max-w-[80%]">
                      Your claim CLM-2026-0142 is currently <strong>Under Review</strong>. It was submitted on Sep 2 and has been assigned to an adjuster. Estimated completion: 2-3 business days.
                    </div>
                  </div>
                  <div className="flex justify-end">
                    <div className="bg-blue-600 text-white text-sm px-4 py-2.5 rounded-2xl rounded-br-md max-w-[80%]">
                      What should I do next?
                    </div>
                  </div>
                  <div className="flex justify-start">
                    <div className="bg-slate-100 text-slate-700 text-sm px-4 py-2.5 rounded-2xl rounded-bl-md max-w-[80%]">
                      No action needed right now. I'll notify you when the adjuster completes their review. You can also upload supporting documents in the claim detail page.
                    </div>
                  </div>
                </div>
              </Card>
            </div>
          </div>
        </div>
      </section>

      {/* Claims journey */}
      <section className="py-20 bg-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div className="relative order-2 lg:order-1">
              <div className="absolute inset-0 bg-gradient-to-br from-blue-100/40 to-navy-100/30 rounded-3xl blur-2xl" />
              <ClaimsIllustration className="relative w-full h-auto max-w-sm mx-auto" />
            </div>
            <div className="order-1 lg:order-2">
              <Badge tone="blue" className="mb-3">Claim Journey</Badge>
              <h2 className="text-3xl sm:text-4xl font-bold text-slate-900 tracking-tight mb-4">
                From filing to payout in record time
              </h2>
              <p className="text-slate-600 mb-8">Our guided claim process walks you through every step, with real-time updates and AI assistance along the way.</p>
              <div className="space-y-4">
                {[
                  { step: '01', title: 'Select your policy', desc: 'Choose which policy your claim falls under.' },
                  { step: '02', title: 'Describe the incident', desc: 'Tell us what happened with our guided form.' },
                  { step: '03', title: 'Track in real-time', desc: 'Watch your claim progress through each stage.' },
                  { step: '04', title: 'Get AI assistance', desc: 'Ask questions and get instant answers anytime.' },
                ].map((item) => (
                  <div key={item.step} className="flex gap-4 items-start">
                    <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-navy-900 text-white font-bold text-sm flex items-center justify-center">
                      {item.step}
                    </div>
                    <div>
                      <h3 className="font-semibold text-slate-900">{item.title}</h3>
                      <p className="text-sm text-slate-500">{item.desc}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Trust */}
      <section className="py-16 bg-slate-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid md:grid-cols-3 gap-6">
            {[
              { icon: Award, title: 'Award-Winning Service', desc: 'Recognized as a top insurtech platform for customer experience.' },
              { icon: Shield, title: 'Fully Licensed', desc: 'Licensed in all 50 states with A+ financial strength rating.' },
              { icon: Users, title: 'Customer-First', desc: 'We prioritize your needs with dedicated support and transparent processes.' },
            ].map((item) => (
              <Card key={item.title} className="p-6 flex items-start gap-4">
                <div className="flex-shrink-0 w-12 h-12 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-center">
                  <item.icon className="w-6 h-6 text-blue-600" />
                </div>
                <div>
                  <h3 className="font-semibold text-slate-900">{item.title}</h3>
                  <p className="text-sm text-slate-500 mt-1">{item.desc}</p>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-20 bg-gradient-to-br from-navy-900 via-navy-800 to-blue-900 relative overflow-hidden">
        <div className="absolute inset-0 opacity-10" style={{ backgroundImage: 'radial-gradient(circle at 20% 50%, #60a5fa 0%, transparent 50%), radial-gradient(circle at 80% 50%, #3b82f6 0%, transparent 50%)' }} />
        <div className="relative max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <AIIllustration className="w-32 h-26 mx-auto mb-6" />
          <h2 className="text-3xl sm:text-4xl font-bold text-white tracking-tight mb-4">
            Ready to experience modern insurance?
          </h2>
          <p className="text-lg text-slate-300 mb-8 max-w-2xl mx-auto">
            Join thousands of customers who've made the switch to ClaimAssist. Get started in minutes with free sample policies.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Button size="lg" onClick={() => navigate('/register')}>
              Create Free Account <ArrowRight className="w-5 h-5" />
            </Button>
            <Button size="lg" variant="secondary" onClick={() => navigate('/contact')}>
              Talk to Us
            </Button>
          </div>
        </div>
      </section>
    </div>
  );
}
