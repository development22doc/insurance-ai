import { type ReactNode } from 'react';

type Tone = 'blue' | 'green' | 'amber' | 'red' | 'slate' | 'navy';

interface BadgeProps {
  children: ReactNode;
  tone?: Tone;
  className?: string;
  dot?: boolean;
}

const toneClasses: Record<Tone, string> = {
  blue: 'bg-blue-50 text-blue-700 border-blue-200',
  green: 'bg-green-50 text-green-700 border-green-200',
  amber: 'bg-amber-50 text-amber-700 border-amber-200',
  red: 'bg-red-50 text-red-700 border-red-200',
  slate: 'bg-slate-100 text-slate-600 border-slate-200',
  navy: 'bg-navy-50 text-navy-700 border-navy-200',
};

const dotClasses: Record<Tone, string> = {
  blue: 'bg-blue-500',
  green: 'bg-green-500',
  amber: 'bg-amber-500',
  red: 'bg-red-500',
  slate: 'bg-slate-400',
  navy: 'bg-navy-600',
};

export function Badge({ children, tone = 'slate', className = '', dot = false }: BadgeProps) {
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border ${toneClasses[tone]} ${className}`}>
      {dot && <span className={`w-1.5 h-1.5 rounded-full ${dotClasses[tone]}`} />}
      {children}
    </span>
  );
}

export function statusTone(status: string): Tone {
  switch (status) {
    case 'Active':
    case 'Approved':
    case 'Payout':
      return 'green';
    case 'Submitted':
      return 'blue';
    case 'Under Review':
    case 'Pending':
      return 'amber';
    case 'Rejected':
    case 'Expired':
      return 'red';
    default:
      return 'slate';
  }
}
