import { Car, Home, Heart, Plane, Umbrella, ArrowRight, Check } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { navigate } from '@/lib/router';

export function ProductsPage() {
  const products = [
    {
      icon: Car,
      name: 'Auto Insurance',
      tagline: 'Drive with confidence',
      desc: 'Comprehensive and collision coverage with roadside assistance, rental reimbursement, and instant claim filing.',
      features: ['Liability & collision', 'Roadside assistance', 'Rental reimbursement', 'Instant claim filing', '24/7 support'],
      price: 'From $50/mo',
      gradient: 'from-blue-500 to-blue-700',
    },
    {
      icon: Home,
      name: 'Home Insurance',
      tagline: 'Protect your sanctuary',
      desc: 'Full property and dwelling coverage including personal belongings, liability protection, and temporary living costs.',
      features: ['Dwelling & property', 'Personal belongings', 'Liability protection', 'Loss of use', 'Natural disaster add-ons'],
      price: 'From $89/mo',
      gradient: 'from-navy-600 to-navy-800',
    },
    {
      icon: Heart,
      name: 'Health Insurance',
      tagline: 'Your health, covered',
      desc: 'Comprehensive health plans with nationwide network access, preventive care, and prescription coverage.',
      features: ['Nationwide network', 'Preventive care', 'Prescription coverage', 'Specialist visits', 'Telehealth included'],
      price: 'From $120/mo',
      gradient: 'from-green-500 to-green-700',
    },
    {
      icon: Plane,
      name: 'Travel Insurance',
      tagline: 'Explore worry-free',
      desc: 'Trip cancellation, medical emergencies, lost baggage, and flight delays — all in one travel policy.',
      features: ['Trip cancellation', 'Medical emergencies', 'Lost baggage', 'Flight delays', 'Worldwide coverage'],
      price: 'From $25/trip',
      gradient: 'from-blue-500 to-navy-700',
    },
    {
      icon: Umbrella,
      name: 'Life Insurance',
      tagline: 'Secure their future',
      desc: 'Term and whole life policies with flexible coverage amounts and guaranteed payout for your beneficiaries.',
      features: ['Term & whole life', 'Flexible coverage', 'Guaranteed payout', 'No medical exam options', 'Living benefits'],
      price: 'From $30/mo',
      gradient: 'from-navy-700 to-navy-900',
    },
  ];

  return (
    <div className="animate-fade-in">
      {/* Hero */}
      <section className="relative bg-gradient-to-b from-blue-50/50 to-white py-16 lg:py-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <Badge tone="blue" className="mb-4">Our Products</Badge>
          <h1 className="text-4xl sm:text-5xl font-bold text-slate-900 tracking-tight mb-4">
            Coverage for every part of life
          </h1>
          <p className="text-lg text-slate-600 max-w-2xl mx-auto">
            Explore our full range of insurance products, each designed with fast claims, transparent pricing, and AI-powered support.
          </p>
        </div>
      </section>

      {/* Product cards */}
      <section className="py-12 lg:py-16 bg-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            {products.map((product) => (
              <Card key={product.name} hover className="overflow-hidden flex flex-col">
                {/* Gradient header */}
                <div className={`bg-gradient-to-br ${product.gradient} p-6`}>
                  <product.icon className="w-10 h-10 text-white mb-3" strokeWidth={1.5} />
                  <h3 className="text-xl font-bold text-white">{product.name}</h3>
                  <p className="text-sm text-white/80 mt-0.5">{product.tagline}</p>
                </div>
                {/* Body */}
                <div className="p-6 flex-1 flex flex-col">
                  <p className="text-sm text-slate-600 leading-relaxed mb-4">{product.desc}</p>
                  <ul className="space-y-2 mb-5 flex-1">
                    {product.features.map((feat) => (
                      <li key={feat} className="flex items-center gap-2 text-sm text-slate-700">
                        <Check className="w-4 h-4 text-green-500 flex-shrink-0" />
                        {feat}
                      </li>
                    ))}
                  </ul>
                  <div className="flex items-center justify-between pt-4 border-t border-slate-100">
                    <div>
                      <p className="text-xs text-slate-400">Starting at</p>
                      <p className="text-lg font-bold text-slate-900">{product.price}</p>
                    </div>
                    <Button size="sm" onClick={() => navigate('/register')}>Get Covered <ArrowRight className="w-4 h-4" /></Button>
                  </div>
                </div>
              </Card>
            ))}

            {/* Bundle card */}
            <Card className="bg-gradient-to-br from-navy-900 to-navy-800 border-0 flex flex-col">
              <div className="p-6 flex-1 flex flex-col justify-between">
                <div>
                  <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-white/10 mb-4">
                    <Umbrella className="w-6 h-6 text-blue-300" />
                  </div>
                  <h3 className="text-xl font-bold text-white">Bundle & Save</h3>
                  <p className="text-sm text-slate-400 mt-1">Combine multiple policies and save up to 25%</p>
                  <ul className="space-y-2 mt-4">
                    {['Multi-policy discount', 'Single deductible', 'Unified billing', 'Priority claims'].map((f) => (
                      <li key={f} className="flex items-center gap-2 text-sm text-slate-300">
                        <Check className="w-4 h-4 text-green-400 flex-shrink-0" />
                        {f}
                      </li>
                    ))}
                  </ul>
                </div>
                <Button variant="secondary" className="mt-5" onClick={() => navigate('/register')}>Build Your Bundle</Button>
              </div>
            </Card>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-16 bg-slate-50">
        <div className="max-w-3xl mx-auto px-4 text-center">
          <h2 className="text-2xl sm:text-3xl font-bold text-slate-900 mb-3">Not sure which plan is right for you?</h2>
          <p className="text-slate-600 mb-6">Our team can help you find the perfect coverage for your needs and budget.</p>
          <div className="flex gap-3 justify-center">
            <Button size="lg" onClick={() => navigate('/contact')}>Talk to an Agent</Button>
            <Button size="lg" variant="secondary" onClick={() => navigate('/register')}>Sign Up Now</Button>
          </div>
        </div>
      </section>
    </div>
  );
}
