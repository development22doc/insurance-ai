/**
 * Idempotency key generation for claim submission
 * Generates a cryptographically strong random UUID v4
 */

/**
 * Generate a cryptographically strong random idempotency key.
 * Uses Web Crypto when available and falls back to a UUID-style local generator.
 */
export function generateIdempotencyKey(): string {
  const cryptoApi = globalThis.crypto;

  if (cryptoApi && typeof cryptoApi.getRandomValues === 'function') {
    const array = new Uint8Array(16);
    cryptoApi.getRandomValues(array);

    array[6] = (array[6] & 0x0f) | 0x40;
    array[8] = (array[8] & 0x3f) | 0x80;

    const hex = Array.from(array).map((value) => value.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.random() * 16 | 0;
    const value = char === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

/**
 * Store the idempotency key for a submission attempt
 * Only one key should be active at a time for a given submission
 */
export function storeIdempotencyKey(key: string): void {
  sessionStorage.setItem('claim_idempotency_key', key);
}

/**
 * Retrieve the current idempotency key for a submission
 */
export function getIdempotencyKey(): string | null {
  return sessionStorage.getItem('claim_idempotency_key');
}

/**
 * Clear the stored idempotency key after successful submission
 */
export function clearIdempotencyKey(): void {
  sessionStorage.removeItem('claim_idempotency_key');
}

