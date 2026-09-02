import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../services/api-client';
import { API_ENDPOINTS } from '../config/api';
import type { AgentMessageResponse, AgentRequest, StreamResponse } from '../types';

interface AIAssistantProps {
  claimId: number;
}

export const AIAssistant: React.FC<AIAssistantProps> = ({ claimId }) => {
  const [history, setHistory] = useState<AgentMessageResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [streamError, setStreamError] = useState<string | null>(null);
  const readerRef = useRef<ReadableStreamDefaultReader<Uint8Array> | null>(null);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await apiClient.get<AgentMessageResponse[]>(API_ENDPOINTS.AGENT_CLAIM_HISTORY(claimId));
        if (!mounted) return;
        setHistory(data || []);
      } catch (err: any) {
        if (!mounted) return;
        if (err instanceof Error && /403/.test(err.message)) {
          setError('Not authorized to view or use the AI for this claim.');
        } else if (err instanceof Error && /404/.test(err.message)) {
          setError('AI history not found for this claim.');
        } else {
          setError((err && err.message) || 'Unable to load AI history.');
        }
        setHistory([]);
      } finally {
        if (mounted) setLoading(false);
      }
    };
    void load();
    return () => {
      mounted = false;
      if (readerRef.current) {
        try { readerRef.current.cancel(); } catch (e) { /* ignore */ }
      }
    };
  }, [claimId]);

  const parseSSE = async (stream: ReadableStream<Uint8Array>) => {
    const reader = stream.getReader();
    readerRef.current = reader;
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        let idx: number;
        while ((idx = buffer.indexOf('\n\n')) !== -1) {
          const rawEvent = buffer.slice(0, idx).trim();
          buffer = buffer.slice(idx + 2);
          const lines = rawEvent.split(/\r?\n/);
          const dataLines = lines.filter((l) => l.startsWith('data:'));
          if (dataLines.length === 0) continue;
          const payload = dataLines.map((l) => l.replace(/^data:\s?/, '')).join('\n');

          try {
            const event: StreamResponse = JSON.parse(payload);
            handleStreamEvent(event);
          } catch (parseErr) {
            setStreamError('Received malformed stream event');
          }
        }
      }

      if (buffer.trim()) {
        const lines = buffer.split(/\r?\n/).filter((l) => l.startsWith('data:'));
        if (lines.length > 0) {
          const payload = lines.map((l) => l.replace(/^data:\s?/, '')).join('\n');
          try {
            const event: StreamResponse = JSON.parse(payload);
            handleStreamEvent(event);
          } catch (parseErr) {
            setStreamError('Received malformed stream event');
          }
        }
      }
    } catch (err: any) {
      if (err?.name === 'AbortError') return;
      setStreamError(err?.message || 'Stream failed');
    } finally {
      try { reader.cancel(); } catch (e) { /* ignore */ }
      readerRef.current = null;
      setSending(false);
    }
  };

  const handleStreamEvent = (event: StreamResponse) => {
    if (event.eventType === 'message') {
      setHistory((prev) => {
        const current = prev ? [...prev] : [];
        const last = current[current.length - 1];
        if (!last || last.role !== 'ASSISTANT' || (last && last.content === undefined)) {
          current.push({ id: -1, role: 'ASSISTANT', content: event.text, tokensUsed: undefined, createdAt: new Date().toISOString(), events: [] });
        } else {
          const appended = { ...last, content: (last.content || '') + event.text };
          current[current.length - 1] = appended;
        }
        return current;
      });
    } else if (event.eventType === 'done') {
      // terminal - nothing extra to do here
    } else if (event.eventType === 'error') {
      setStreamError(event.errorCode ?? 'AI_ERROR');
    }
  };

  const handleSend = async () => {
    if (!input || input.trim().length === 0) return;
    setStreamError(null);
    setSending(true);

    setHistory((prev) => {
      const current = prev ? [...prev] : [];
      current.push({ id: -1, role: 'USER', content: input.trim(), tokensUsed: undefined, createdAt: new Date().toISOString(), events: [] });
      return current;
    });

    const payload: AgentRequest = { message: input.trim(), claimId };
    setInput('');

    try {
      const stream = await apiClient.stream(API_ENDPOINTS.AGENT_STREAM, payload);
      void parseSSE(stream);
    } catch (err: any) {
      setStreamError((err && err.message) || 'Unable to start AI stream');
      setSending(false);
    }
  };

  const handleStop = () => {
    setSending(false);
    setStreamError('Cancelled');
    if (readerRef.current) {
      try { readerRef.current.cancel(); } catch (e) { /* ignore */ }
      readerRef.current = null;
    }
  };

  return (
    <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] p-4">
      <h3 className="text-lg font-semibold">Claim AI Assistant</h3>
      <p className="mt-1 text-sm text-[var(--color-text-secondary)]">Ask the assistant about this claim. Your access is controlled by backend permissions.</p>

      {loading && <div className="mt-3 text-sm">Loading assistant...</div>}
      {error && <div className="mt-3 text-sm text-red-600">{error}</div>}

      <div className="mt-3 max-h-64 overflow-auto rounded-md bg-white p-3">
        {Array.isArray(history) && history.length === 0 && <div className="text-sm text-[var(--color-text-secondary)]">No prior assistant messages.</div>}
        {Array.isArray(history) && history.map((m, idx) => (
          <div key={`${m.role}-${idx}`} className={`mb-3 ${m.role === 'USER' ? '' : 'bg-[var(--color-surface)] p-2 rounded'}`}>
            <div className="text-xs text-[var(--color-text-secondary)]">{m.role}</div>
            <div className="whitespace-pre-wrap">{m.content}</div>
          </div>
        ))}
      </div>

      <div className="mt-3 flex gap-2">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          rows={2}
          className="flex-1 rounded-lg border border-[var(--color-border)] bg-white px-3 py-2 text-sm focus:border-[var(--color-primary)] focus:outline-none"
          placeholder="Ask about damages, status, documents..."
          disabled={sending || Boolean(error)}
        />
        <div className="flex flex-col gap-2">
          <button
            onClick={handleSend}
            disabled={sending || Boolean(error) || input.trim().length === 0}
            className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
          >
            {sending ? 'Sending…' : 'Send'}
          </button>
          {sending && (
            <button
              onClick={handleStop}
              className="rounded-lg border border-[var(--color-border)] px-3 py-1 text-xs"
            >
              Stop
            </button>
          )}
        </div>
      </div>

      {streamError && <div className="mt-2 text-sm text-red-600">{streamError}</div>}
    </div>
  );
};

export default AIAssistant;
