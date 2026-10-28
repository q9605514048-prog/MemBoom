import { webcrypto } from 'node:crypto';

const { subtle } = webcrypto;
const encoder = new TextEncoder();

function encode(bytes) { return Buffer.from(bytes).toString('base64url'); }
function decode(value) { return new Uint8Array(Buffer.from(value, 'base64url')); }

async function keyFromRecovery(recoveryKey, salt) {
  const material = await subtle.importKey('raw', encoder.encode(recoveryKey), 'PBKDF2', false, ['deriveKey']);
  return subtle.deriveKey({ name: 'PBKDF2', salt, iterations: 310_000, hash: 'SHA-256' }, material, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
}

export async function createRecoveryKey() {
  const bytes = webcrypto.getRandomValues(new Uint8Array(32));
  return { key: encode(bytes), display: encode(bytes) };
}

export async function protectVaultKey(vaultKey, recoveryKey) {
  const salt = webcrypto.getRandomValues(new Uint8Array(16));
  const nonce = webcrypto.getRandomValues(new Uint8Array(12));
  const key = await keyFromRecovery(recoveryKey, salt);
  const ciphertext = await subtle.encrypt({ name: 'AES-GCM', iv: nonce }, key, vaultKey);
  return { version: 1, salt: encode(salt), nonce: encode(nonce), ciphertext: encode(new Uint8Array(ciphertext)) };
}

export async function recoverVaultKey(wrapped, recoveryKey) {
  if (!wrapped || wrapped.version !== 1) throw new Error('Unsupported recovery envelope');
  const key = await keyFromRecovery(recoveryKey, decode(wrapped.salt));
  const plaintext = await subtle.decrypt({ name: 'AES-GCM', iv: decode(wrapped.nonce) }, key, decode(wrapped.ciphertext));
  return new Uint8Array(plaintext);
}
