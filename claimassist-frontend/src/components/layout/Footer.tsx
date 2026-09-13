import { Shield, Mail, Phone, MapPin } from 'lucide-react';
import { navigate } from '@/lib/router';

export function Footer() {
  const link = (path: string, label: string) => (
    <button onClick={() => navigate(path)} className="text-sm text-slate-400 hover:text-white transition-colors text-left">
      {label}
    </button>
  );

  return (
    <footer className="bg-navy-900 text-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* Brand */}
          <div className="md:col-span-1">
            <div className="flex items-center gap-2.5 mb-3">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-blue-500 to-blue-700 flex items-center justify-center">
                <Shield className="w-5 h-5 text-white" strokeWidth={2.5} />
              </div>
              <span className="text-lg font-bold tracking-tight">ClaimAssist</span>
            </div>
            <p className="text-sm text-slate-400 leading-relaxed">
              Modern insurance, simplified. File claims, track progress, and get AI-powered assistance — all in one place.
            </p>
          </div>

          {/* Products */}
          <div>
            <h4 className="text-sm font-semibold mb-3 text-slate-200">Products</h4>
            <div className="space-y-2">
              {link('/products', 'Auto Insurance')}
              {link('/products', 'Home Insurance')}
              {link('/products', 'Health Insurance')}
              {link('/products', 'Travel Insurance')}
            </div>
          </div>

          {/* Company */}
          <div>
            <h4 className="text-sm font-semibold mb-3 text-slate-200">Company</h4>
            <div className="space-y-2">
              {link('/about', 'About Us')}
              {link('/contact', 'Contact')}
              {link('/claims', 'File a Claim')}
              {link('/operations', 'Operations')}
            </div>
          </div>

          {/* Contact */}
          <div>
            <h4 className="text-sm font-semibold mb-3 text-slate-200">Get in Touch</h4>
            <div className="space-y-2 text-sm text-slate-400">
              <div className="flex items-center gap-2"><Mail className="w-4 h-4" /> support@claimassist.com</div>
              <div className="flex items-center gap-2"><Phone className="w-4 h-4" /> 1-800-CLAIM-01</div>
              <div className="flex items-center gap-2"><MapPin className="w-4 h-4" /> 100 Shield Ave, NY</div>
            </div>
          </div>
        </div>

        <div className="mt-10 pt-6 border-t border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p className="text-xs text-slate-500">© 2026 ClaimAssist. All rights reserved.</p>
          <div className="flex gap-5 text-xs text-slate-500">
            <span className="hover:text-slate-300 cursor-pointer transition-colors">Privacy Policy</span>
            <span className="hover:text-slate-300 cursor-pointer transition-colors">Terms of Service</span>
            <span className="hover:text-slate-300 cursor-pointer transition-colors">Licenses</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
