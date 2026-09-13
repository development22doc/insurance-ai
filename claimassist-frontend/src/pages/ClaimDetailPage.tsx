import { useEffect, useState, useRef } from 'react';
import { ArrowLeft, FileText, Calendar, DollarSign, Shield, Bot, Send, Sparkles, AlertCircle, RotateCcw, User } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge, statusTone } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { ClaimTimeline } from '@/components/shared/ClaimTimeline';
import * as api from '@/lib/api';
import type { Claim, Policy } from '@/lib/api';
import { navigate } from '@/lib/router';

const SUGGESTED_QUESTIONS = [
  "What's the status of my claim?",
  "How long will it take?",
  "What should I do next?",
  "What's my coverage amount?",
  "What documents do I need?",
  "How do I contact support?",
];

export function ClaimDetailPage({ claimId }: { claimId: string }) {
  const [claim, setClaim] = useState<Claim | null>(null);
  const [policy, setPolicy] = useState<Policy | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [input, setInput] = useState('');
  const [aiThinking, setAiThinking] = useState(false);
  const [aiError, setAiError] = useState(false);
  const [messages, setMessages] = useState<Array<{ role: 'user' | 'assistant'; content: string }>>([]);
  const messagesContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const loadClaim = async () => {
      try {
        const claimData = await api.getClaim(claimId);
        setClaim(claimData);

        // Try to get policy info
        try {
          const policyData = await api.getPolicy(claimData.policyId);
          setPolicy(policyData);
        } catch {
          // Policy might not be accessible or not found
          setPolicy(null);
        }

        setLoading(false);
      } catch {
        setError('Failed to load claim');
        setLoading(false);
      }
    };

    loadClaim();
  }, [claimId]);

  useEffect(() => {
    const container = messagesContainerRef.current;
    if (!container) return;

    // Check if user is near the bottom (within 100px)
    const isNearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 100;

    // Only auto-scroll if user is already near the bottom
    if (isNearBottom) {
      container.scrollTop = container.scrollHeight;
    }
  }, [messages, aiThinking]);

  const sendMessage = async (text: string) => {
    if (!text.trim() || !claim) return;
    setInput('');
    setAiError(false);

    const userMsg = { role: 'user' as const, content: text };
    setMessages(prev => [...prev, userMsg]);

    // Auto-scroll to bottom when user sends a message
    setTimeout(() => {
      const container = messagesContainerRef.current;
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    }, 0);

    setAiThinking(true);

    try {
      let fullResponse = '';

      await api.streamAIMessage(
        text,
        claimId,
        (chunk) => {
          fullResponse += chunk;
          setMessages(prev => {
            const last = prev[prev.length - 1];
            if (last && last.role === 'assistant') {
              return [...prev.slice(0, -1), { role: 'assistant', content: fullResponse }];
            }
            return [...prev, { role: 'assistant', content: chunk }];
          });
        },
        () => {
          setAiThinking(false);
        },
        (err) => {
          setAiThinking(false);
          setAiError(true);
          setMessages(prev => [...prev, { role: 'assistant', content: `Error: ${err}` }]);
        }
      );
    } catch {
      setAiThinking(false);
      setAiError(true);
      setMessages(prev => [...prev, { role: 'assistant', content: 'Failed to connect to AI assistant. Please try again.' }]);
    }
  };

  const handleRetry = () => {
    setAiError(false);
    if (input.trim()) sendMessage(input);
  };

  if (loading) {
    return <div className="min-h-[60vh] flex items-center justify-center"><Spinner size="lg" /></div>;
  }

  if (error || !claim) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <FileText className="w-12 h-12 text-slate-300 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-slate-900 mb-2">Claim not found</h2>
        <Button variant="secondary" onClick={() => navigate('/claims-list')} className="mt-4">Back to Claims</Button>
      </div>
    );
  }

  const details = [
    { icon: FileText, label: 'Claim Number', value: claim.claimNumber, mono: true },
    { icon: Shield, label: 'Policy', value: policy ? `${policy.productType} Insurance` : 'N/A' },
    { icon: FileText, label: 'Incident Type', value: claim.incidentType },
    { icon: Calendar, label: 'Incident Date', value: new Date(claim.incidentDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }) },
    { icon: DollarSign, label: 'Estimated Amount', value: claim.estimatedAmountCents ? `$${(claim.estimatedAmountCents / 100).toLocaleString()}` : 'N/A' },
    { icon: Calendar, label: 'Filed On', value: new Date(claim.createdAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }) },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      <button onClick={() => navigate('/claims-list')} className="flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-blue-600 transition-colors mb-6">
        <ArrowLeft className="w-4 h-4" /> Back to Claims
      </button>

      {/* Claim header */}
      <Card className="overflow-hidden mb-6">
        <div className="bg-gradient-to-br from-blue-600 to-navy-800 p-6">
          <div className="flex items-start justify-between flex-wrap gap-4">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-2xl bg-white/20 flex items-center justify-center">
                <FileText className="w-7 h-7 text-white" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-white">{claim.incidentType}</h1>
                <p className="text-white/80 mt-0.5 font-mono">{claim.claimNumber}</p>
              </div>
            </div>
            <Badge tone={statusTone(claim.status)} className="bg-white/20 text-white border-white/30 text-sm px-3 py-1.5">
              {claim.status}
            </Badge>
          </div>
        </div>
        <div className="p-6">
          <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
            {details.map((d) => (
              <div key={d.label} className="flex items-center gap-3 p-3 rounded-xl bg-slate-50">
                <div className="w-9 h-9 rounded-lg bg-white border border-slate-200 flex items-center justify-center flex-shrink-0">
                  <d.icon className="w-4 h-4 text-slate-500" />
                </div>
                <div className="min-w-0">
                  <p className="text-xs text-slate-400">{d.label}</p>
                  <p className={`text-sm font-semibold text-slate-900 truncate ${d.mono ? 'font-mono' : ''}`}>{d.value}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </Card>

      <div className="grid lg:grid-cols-5 gap-6">
        {/* Timeline */}
        <div className="lg:col-span-2">
          <Card className="p-6 sticky top-20">
            <h2 className="font-bold text-slate-900 mb-5 flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-blue-500" /> Claim Timeline
            </h2>
            <ClaimTimeline history={[]} currentStatus={claim.status} />
          </Card>
        </div>

        {/* AI Assistant */}
        <div className="lg:col-span-3">
          <Card className="flex flex-col" style={{ minHeight: '500px' }}>
            {/* Header */}
            <div className="p-5 border-b border-slate-100 flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-navy-700 flex items-center justify-center">
                <Bot className="w-5 h-5 text-white" />
              </div>
              <div>
                <p className="font-bold text-slate-900">ClaimAssist AI</p>
                <p className="text-xs text-green-600 flex items-center gap-1">
                  <span className="w-2 h-2 rounded-full bg-green-500" /> Online — Ready to help
                </p>
              </div>
            </div>

            {/* Messages */}
            <div ref={messagesContainerRef} className="flex-1 min-h-0 overflow-y-auto p-5 space-y-4">
              {messages.length === 0 && (
                <div className="text-center py-8">
                  <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-blue-50 border border-blue-100 mb-3">
                    <Bot className="w-7 h-7 text-blue-500" />
                  </div>
                  <p className="font-semibold text-slate-900 text-sm mb-1">Ask me about your claim</p>
                  <p className="text-xs text-slate-500 mb-4">I can help with status, timeline, coverage, and next steps.</p>
                  <div className="grid sm:grid-cols-2 gap-2">
                    {SUGGESTED_QUESTIONS.map((q) => (
                      <button
                        key={q}
                        onClick={() => sendMessage(q)}
                        className="text-left text-xs text-slate-600 bg-slate-50 hover:bg-blue-50 hover:text-blue-700 border border-slate-200 hover:border-blue-200 rounded-lg px-3 py-2 transition-colors"
                      >
                        {q}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {messages.map((msg, idx) => (
                <div key={idx} className={`flex gap-2.5 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                  {msg.role === 'assistant' && (
                    <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-navy-700 flex items-center justify-center flex-shrink-0">
                      <Bot className="w-4 h-4 text-white" />
                    </div>
                  )}
                  <div className={`max-w-[75%] px-4 py-2.5 text-sm leading-relaxed whitespace-pre-line ${
                    msg.role === 'user'
                      ? 'bg-blue-600 text-white rounded-2xl rounded-br-md'
                      : 'bg-slate-100 text-slate-700 rounded-2xl rounded-bl-md'
                  }`}>
                    {msg.content}
                  </div>
                  {msg.role === 'user' && (
                    <div className="w-8 h-8 rounded-lg bg-slate-200 flex items-center justify-center flex-shrink-0">
                      <User className="w-4 h-4 text-slate-500" />
                    </div>
                  )}
                </div>
              ))}

              {aiThinking && (
                <div className="flex gap-2.5 justify-start">
                  <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-navy-700 flex items-center justify-center flex-shrink-0">
                    <Bot className="w-4 h-4 text-white" />
                  </div>
                  <div className="bg-slate-100 rounded-2xl rounded-bl-md px-4 py-3 flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-blue-400 animate-bounce" style={{ animationDelay: '0ms' }} />
                    <span className="w-2 h-2 rounded-full bg-blue-400 animate-bounce" style={{ animationDelay: '150ms' }} />
                    <span className="w-2 h-2 rounded-full bg-blue-400 animate-bounce" style={{ animationDelay: '300ms' }} />
                  </div>
                </div>
              )}

              {aiError && (
                <div className="flex gap-2.5 justify-start">
                  <div className="w-8 h-8 rounded-lg bg-red-100 flex items-center justify-center flex-shrink-0">
                    <AlertCircle className="w-4 h-4 text-red-500" />
                  </div>
                  <div className="bg-red-50 border border-red-200 rounded-2xl rounded-bl-md px-4 py-3">
                    <p className="text-sm text-red-700 mb-2">Something went wrong. Please try again.</p>
                    <Button size="sm" variant="secondary" onClick={handleRetry}>
                      <RotateCcw className="w-3.5 h-3.5" /> Retry
                    </Button>
                  </div>
                </div>
              )}
            </div>

            {/* Input */}
            <div className="p-4 border-t border-slate-100">
              <div className="flex gap-2">
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(input); } }}
                  placeholder="Ask about your claim..."
                  className="flex-1 rounded-xl border border-slate-200 px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                />
                <Button onClick={() => sendMessage(input)} disabled={!input.trim() || aiThinking}>
                  <Send className="w-4 h-4" />
                </Button>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
