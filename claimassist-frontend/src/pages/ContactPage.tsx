import { Mail, Phone, MapPin, Clock } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { ComingSoon } from '@/components/shared/ComingSoon';
import { SupportIllustration } from '@/components/shared/Illustrations';

export function ContactPage() {
  const contactInfo = [
    { icon: Mail, label: 'Email', value: 'support@claimassist.com', sub: 'We reply within 24 hours' },
    { icon: Phone, label: 'Phone', value: '1-800-CLAIM-01', sub: 'Mon-Fri, 8am-8pm EST' },
    { icon: MapPin, label: 'Office', value: '100 Shield Ave, New York, NY 10001', sub: 'Visit us in person' },
  ];

  return (
    <div className="animate-fade-in">
      {/* Hero */}
      <section className="relative bg-gradient-to-b from-blue-50/50 to-white py-16 lg:py-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <Badge tone="blue" className="mb-4">Get in Touch</Badge>
          <h1 className="text-4xl sm:text-5xl font-bold text-slate-900 tracking-tight mb-4">
            We're here to help
          </h1>
          <p className="text-lg text-slate-600 max-w-2xl mx-auto">
            Have a question about your policy, a claim, or anything else? Our team is ready to assist you.
          </p>
        </div>
      </section>

      {/* Contact section */}
      <section className="py-16 bg-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid lg:grid-cols-5 gap-8">
            {/* Contact info */}
            <div className="lg:col-span-2 space-y-4">
              <div className="flex justify-center lg:justify-start mb-2">
                <SupportIllustration className="w-48 h-40" />
              </div>
              {contactInfo.map((info) => (
                <Card key={info.label} className="p-5 flex items-start gap-4">
                  <div className="flex-shrink-0 w-11 h-11 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-center">
                    <info.icon className="w-5 h-5 text-blue-600" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide">{info.label}</p>
                    <p className="text-sm font-semibold text-slate-900 mt-0.5">{info.value}</p>
                    <p className="text-xs text-slate-500 mt-0.5">{info.sub}</p>
                  </div>
                </Card>
              ))}
              <Card className="p-5 bg-gradient-to-br from-navy-900 to-navy-800 border-0">
                <div className="flex items-center gap-3 mb-2">
                  <Clock className="w-5 h-5 text-blue-300" />
                  <p className="text-sm font-semibold text-white">Response Time</p>
                </div>
                <p className="text-xs text-slate-400">Average response time is under 2 hours during business hours. For urgent claim matters, call our hotline directly.</p>
              </Card>
            </div>

            {/* Form - Coming Soon */}
            <div className="lg:col-span-3">
              <Card className="p-8">
                <ComingSoon
                  title="Online Contact Form"
                  description="Our online contact submission feature is coming soon. For now, please reach out via email or phone."
                />
              </Card>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
