import { BarChart3, Settings, Users, TrendingUp } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { ComingSoon } from '@/components/shared/ComingSoon';

export function OperationsPage() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      <div className="mb-8">
        <Badge tone="blue" className="mb-3">Operations Workspace</Badge>
        <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight mb-2">
          Insurance Operations Center
        </h1>
        <p className="text-slate-500 max-w-2xl">
          A unified workspace for claims processing, policy administration, and customer management. This area is being built for operations teams.
        </p>
      </div>

      {/* Overview stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {[
          { label: 'Total Claims', value: '—', icon: BarChart3, tone: 'bg-blue-50 text-blue-600' },
          { label: 'Pending Review', value: '—', icon: TrendingUp, tone: 'bg-amber-50 text-amber-600' },
          { label: 'Active Policies', value: '—', icon: Settings, tone: 'bg-green-50 text-green-600' },
          { label: 'Customers', value: '—', icon: Users, tone: 'bg-navy-50 text-navy-700' },
        ].map((stat) => (
          <Card key={stat.label} className="p-5">
            <div className={`inline-flex items-center justify-center w-10 h-10 rounded-xl ${stat.tone} mb-3`}>
              <stat.icon className="w-5 h-5" />
            </div>
            <p className="text-2xl font-bold text-slate-900">{stat.value}</p>
            <p className="text-xs text-slate-500 mt-0.5">{stat.label}</p>
          </Card>
        ))}
      </div>

      {/* Feature roadmap */}
      <div className="grid md:grid-cols-3 gap-5 mb-8">
        {[
          { icon: BarChart3, title: 'Claims Queue', desc: 'Review and process incoming claims with priority sorting and batch actions.' },
          { icon: Settings, title: 'Policy Admin', desc: 'Create, modify, and manage policy templates and coverage configurations.' },
          { icon: Users, title: 'Customer Management', desc: 'View customer profiles, policy history, and claim records in one place.' },
        ].map((feature) => (
          <Card key={feature.title} className="p-6">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-blue-50 border border-blue-100 mb-4">
              <feature.icon className="w-6 h-6 text-blue-600" />
            </div>
            <h3 className="text-lg font-semibold text-slate-900 mb-1.5">{feature.title}</h3>
            <p className="text-sm text-slate-500 leading-relaxed mb-4">{feature.desc}</p>
            <Badge tone="amber" dot>Coming Soon</Badge>
          </Card>
        ))}
      </div>

      <ComingSoon
        title="Operations Workspace"
        description="The full operations workspace with claims processing, policy administration, and analytics dashboards is coming soon. This will be available for operations team members."
        icon={<Settings className="w-8 h-8 text-blue-500" />}
        actionLabel="Back to Dashboard"
        actionPath="/dashboard"
      />
    </div>
  );
}
