import { CheckCircle2, Circle, Clock } from 'lucide-react';
import { statusTone } from '@/components/ui/Badge';

interface ClaimStatusHistory {
  status: string;
  note?: string | null;
  created_at: string;
}

interface ClaimTimelineProps {
  history: ClaimStatusHistory[];
  currentStatus: string;
}

const ALL_STATUSES = ['Submitted', 'Under Review', 'Approved', 'Payout'];

export function ClaimTimeline({ history, currentStatus }: ClaimTimelineProps) {
  const statusOrder = (status: string) => {
    if (status === 'Rejected') return -1;
    return ALL_STATUSES.indexOf(status);
  };

  const currentIndex = statusOrder(currentStatus);
  const isRejected = currentStatus === 'Rejected';

  return (
    <div className="space-y-0">
      {ALL_STATUSES.map((status, idx) => {
        const historyEntry = history.find(h => h.status === status);
        const isComplete = !isRejected && idx <= currentIndex;
        const isCurrent = !isRejected && idx === currentIndex;
        const isRejectedStep = isRejected && idx === 0;

        return (
          <div key={status} className="flex gap-4 pb-6 last:pb-0">
            {/* Vertical line + dot */}
            <div className="flex flex-col items-center">
              {isComplete && !isCurrent ? (
                <div className="w-9 h-9 rounded-full bg-green-100 border-2 border-green-500 flex items-center justify-center flex-shrink-0">
                  <CheckCircle2 className="w-5 h-5 text-green-600" />
                </div>
              ) : isCurrent ? (
                <div className="w-9 h-9 rounded-full bg-blue-100 border-2 border-blue-500 flex items-center justify-center flex-shrink-0 relative">
                  <Clock className="w-4 h-4 text-blue-600" />
                  <span className="absolute -top-1 -right-1 flex h-3 w-3">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75" />
                    <span className="relative inline-flex rounded-full h-3 w-3 bg-blue-500" />
                  </span>
                </div>
              ) : isRejectedStep ? (
                <div className="w-9 h-9 rounded-full bg-red-100 border-2 border-red-500 flex items-center justify-center flex-shrink-0">
                  <Circle className="w-4 h-4 text-red-500" />
                </div>
              ) : (
                <div className="w-9 h-9 rounded-full bg-slate-50 border-2 border-slate-200 flex items-center justify-center flex-shrink-0">
                  <Circle className="w-4 h-4 text-slate-300" />
                </div>
              )}
              {idx < ALL_STATUSES.length - 1 && (
                <div className={`w-0.5 flex-1 mt-1 ${isComplete && idx < currentIndex ? 'bg-green-300' : 'bg-slate-200'}`} style={{ minHeight: '24px' }} />
              )}
            </div>

            {/* Content */}
            <div className="flex-1 pt-1">
              <div className="flex items-center gap-2 flex-wrap">
                <p className={`text-sm font-semibold ${isComplete ? 'text-slate-900' : 'text-slate-400'}`}>
                  {status}
                </p>
                {isCurrent && !isRejected && (
                  <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs font-semibold rounded-full border bg-${statusTone(currentStatus)}-50 border-${statusTone(currentStatus)}-200 text-${statusTone(currentStatus)}-700`}>
                    Current
                  </span>
                )}
              </div>
              {historyEntry ? (
                <p className="text-xs text-slate-400 mt-0.5">
                  {new Date(historyEntry.created_at).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                  {historyEntry.note && ` · ${historyEntry.note}`}
                </p>
              ) : (
                <p className="text-xs text-slate-400 mt-0.5">
                  {isComplete ? 'Completed' : 'Pending'}
                </p>
              )}
            </div>
          </div>
        );
      })}
      {isRejected && (
        <div className="flex gap-4">
          <div className="w-9 h-9 rounded-full bg-red-100 border-2 border-red-500 flex items-center justify-center flex-shrink-0">
            <Circle className="w-4 h-4 text-red-500" />
          </div>
          <div className="flex-1 pt-1">
            <p className="text-sm font-semibold text-red-600">Rejected</p>
            <p className="text-xs text-slate-400 mt-0.5">This claim was not approved. See details or contact support.</p>
          </div>
        </div>
      )}
    </div>
  );
}
