import { Shield, Target, Eye, Heart, Users, TrendingUp, Award, Globe } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { navigate } from '@/lib/router';

export function AboutPage() {
  const values = [
    { icon: Shield, title: 'Trust', desc: 'We protect what matters to you with honesty and integrity at every step.' },
    { icon: Eye, title: 'Transparency', desc: 'No hidden fees, no fine print. We make insurance clear and understandable.' },
    { icon: Heart, title: 'Customer-First', desc: 'Every decision starts with what is best for our customers.' },
    { icon: TrendingUp, title: 'Innovation', desc: 'We use technology to make insurance faster, simpler, and smarter.' },
  ];

  const milestones = [
    { year: '2021', title: 'ClaimAssist Founded', desc: 'Started with a mission to simplify insurance claims.' },
    { year: '2022', title: '50K Customers', desc: 'Rapid growth driven by our customer-first approach.' },
    { year: '2023', title: 'AI Assistant Launch', desc: 'Introduced our AI-powered claim assistant for 24/7 support.' },
    { year: '2024', title: '500K Claims Processed', desc: 'Reached half a million claims with 98% satisfaction.' },
    { year: '2026', title: 'Nationwide Coverage', desc: 'Now licensed in all 50 states with full digital experience.' },
  ];

  return (
    <div className="animate-fade-in">
      {/* Hero */}
      <section className="relative bg-gradient-to-b from-blue-50/50 to-white py-16 lg:py-24">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-3xl">
            <Badge tone="blue" className="mb-4">About ClaimAssist</Badge>
            <h1 className="text-4xl sm:text-5xl font-bold text-slate-900 tracking-tight mb-5">
              We're reimagining insurance for the digital age
            </h1>
            <p className="text-lg text-slate-600 leading-relaxed">
              ClaimAssist was born from a simple idea: insurance should be easy to understand, simple to manage, and fast when you need it most. We combine modern technology with genuine care to deliver an experience that puts customers first.
            </p>
          </div>
        </div>
      </section>

      {/* Mission & Vision */}
      <section className="py-16 bg-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid md:grid-cols-2 gap-6">
            <Card className="p-8 bg-gradient-to-br from-blue-50 to-white border-blue-100">
              <Target className="w-10 h-10 text-blue-600 mb-4" />
              <h2 className="text-2xl font-bold text-slate-900 mb-2">Our Mission</h2>
              <p className="text-slate-600 leading-relaxed">
                To make insurance accessible, transparent, and stress-free for everyone. We remove the complexity from claims and give customers the tools they need to navigate insurance with confidence.
              </p>
            </Card>
            <Card className="p-8 bg-gradient-to-br from-navy-50 to-white border-navy-100">
              <Globe className="w-10 h-10 text-navy-700 mb-4" />
              <h2 className="text-2xl font-bold text-slate-900 mb-2">Our Vision</h2>
              <p className="text-slate-600 leading-relaxed">
                A world where filing an insurance claim is as easy as ordering food online. Where AI assists you instantly, and where trust is built through transparency, not fine print.
              </p>
            </Card>
          </div>
        </div>
      </section>

      {/* Values */}
      <section className="py-16 bg-slate-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-12">
            <Badge tone="blue" className="mb-3">Our Values</Badge>
            <h2 className="text-3xl font-bold text-slate-900 tracking-tight">What we stand for</h2>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {values.map((value) => (
              <Card key={value.title} className="p-6 text-center">
                <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-blue-50 border border-blue-100 mb-4">
                  <value.icon className="w-7 h-7 text-blue-600" />
                </div>
                <h3 className="text-lg font-semibold text-slate-900 mb-1.5">{value.title}</h3>
                <p className="text-sm text-slate-500 leading-relaxed">{value.desc}</p>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* Timeline */}
      <section className="py-16 lg:py-20 bg-white">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-12">
            <Badge tone="blue" className="mb-3">Our Journey</Badge>
            <h2 className="text-3xl font-bold text-slate-900 tracking-tight">Milestones we're proud of</h2>
          </div>
          <div className="space-y-0">
            {milestones.map((m, idx) => (
              <div key={m.year} className="flex gap-6 pb-8 last:pb-0">
                <div className="flex flex-col items-center">
                  <div className="w-12 h-12 rounded-full bg-gradient-to-br from-blue-500 to-navy-700 flex items-center justify-center text-white font-bold text-xs flex-shrink-0">
                    {m.year}
                  </div>
                  {idx < milestones.length - 1 && <div className="w-0.5 flex-1 bg-slate-200 mt-2" />}
                </div>
                <div className="pt-1.5 pb-4">
                  <h3 className="text-lg font-semibold text-slate-900">{m.title}</h3>
                  <p className="text-sm text-slate-500 mt-1">{m.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Stats */}
      <section className="py-16 bg-navy-900">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-8 text-center">
            {[
              { value: '500K+', label: 'Customers', icon: Users },
              { value: '500K+', label: 'Claims Processed', icon: Shield },
              { value: '98%', label: 'Satisfaction', icon: Award },
              { value: '50', label: 'States Licensed', icon: Globe },
            ].map((stat) => (
              <div key={stat.label}>
                <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-white/10 mb-3">
                  <stat.icon className="w-6 h-6 text-blue-300" />
                </div>
                <p className="text-3xl font-bold text-white">{stat.value}</p>
                <p className="text-sm text-slate-400 mt-1">{stat.label}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-16 bg-slate-50">
        <div className="max-w-3xl mx-auto px-4 text-center">
          <h2 className="text-2xl sm:text-3xl font-bold text-slate-900 mb-3">Join the ClaimAssist family</h2>
          <p className="text-slate-600 mb-6">Experience insurance the way it should be — simple, transparent, and built for you.</p>
          <Button size="lg" onClick={() => navigate('/register')}>Get Started Today</Button>
        </div>
      </section>
    </div>
  );
}
