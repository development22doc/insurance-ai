import { type ReactNode } from 'react';
import { Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { navigate } from '@/lib/router';

interface ComingSoonProps {
  title: string;
  description: string;
  icon?: ReactNode;
  actionLabel?: string;
  actionPath?: string;
}

export function ComingSoon({
  title,
  description,
  icon,
  actionLabel = 'Back to Dashboard',
  actionPath = '/dashboard',
}: ComingSoonProps) {
  return (
    <div className="min-h-[60vh] flex items-center justify-center px-4">
      <div className="max-w-md text-center">
        <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-50 border border-blue-100 mb-5">
          {icon || <Sparkles className="w-8 h-8 text-blue-500" />}
        </div>
        <div className="inline-block px-3 py-1 text-xs font-semibold text-blue-600 bg-blue-50 rounded-full border border-blue-100 mb-4">
          UPCOMING FEATURE
        </div>
        <h2 className="text-2xl font-bold text-slate-900 mb-2">{title}</h2>
        <p className="text-slate-500 mb-6 leading-relaxed">{description}</p>
        <div className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-amber-700 bg-amber-50 rounded-full border border-amber-200 mb-6">
          <span className="w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
          COMING SOON
        </div>
        <div>
          <Button variant="secondary" onClick={() => navigate(actionPath)}>{actionLabel}</Button>
        </div>
      </div>
    </div>
  );
}
