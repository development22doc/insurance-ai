import { describe, expect, it, vi } from 'vitest';
import * as api from './api';

describe('streamAIMessage', () => {
  it('handles AgentService SSE payloads that expose text rather than content', async () => {
    const chunks: string[] = [];
    const onComplete = vi.fn();
    const onError = vi.fn();

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body: new ReadableStream({
          start(controller) {
            const encoder = new TextEncoder();
            controller.enqueue(
              encoder.encode(
                'data: {"text":"Your coverage is based on the active policy.","eventType":"message","requestId":"req-1","done":false,"errorCode":null}\n\n'
              )
            );
            controller.enqueue(
              encoder.encode(
                'data: {"text":"","eventType":"done","requestId":"req-1","done":true,"errorCode":null}\n\n'
              )
            );
            controller.close();
          },
        }),
      })
    );

    await api.streamAIMessage('What is my coverage?', '2', (chunk) => chunks.push(chunk), onComplete, onError);

    expect(chunks).toEqual(['Your coverage is based on the active policy.']);
    expect(onError).not.toHaveBeenCalled();
    expect(onComplete).toHaveBeenCalledTimes(1);
  });
});
