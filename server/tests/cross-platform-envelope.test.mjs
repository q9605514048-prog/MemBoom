import { test } from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { createServer } from '../src/server.mjs';

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const password = 'master-password-123';

function base64(bytes) {
  return Buffer.from(bytes).toString('base64');
}

function bytes(value) {
  return Uint8Array.from(Buffer.from(value, 'base64'));
}

async function createWebsiteEnvelope(value) {
  const salt = webcrypto.getRandomValues(new Uint8Array(16));
  const nonce = webcrypto.getRandomValues(new Uint8Array(12));
  const material = await webcrypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']);
  const key = await webcrypto.subtle.deriveKey(
    { name: 'PBKDF2', salt, iterations: 310_000, hash: 'SHA-256' },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
  const ciphertext = await webcrypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonce },
    key,
    encoder.encode(JSON.stringify(value))
  );
  return { version: 1, salt: base64(salt), nonce: base64(nonce), ciphertext: base64(new Uint8Array(ciphertext)) };
}

async function decryptWebsiteEnvelope(envelope) {
  const salt = bytes(envelope.salt);
  const nonce = bytes(envelope.nonce);
  const material = await webcrypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']);
  const key = await webcrypto.subtle.deriveKey(
    { name: 'PBKDF2', salt, iterations: 310_000, hash: 'SHA-256' },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['decrypt']
  );
  const plaintext = await webcrypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce }, key, bytes(envelope.ciphertext));
  return JSON.parse(decoder.decode(plaintext));
}

test('website-compatible envelope survives server round trip and decrypts without cleartext', async () => {
  const server = createServer({ token: 'test-token-123456' });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    const headers = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    const source = { accounts: [{ category: 'Games', name: 'Steam', fields: [{ label: 'Login', value: 'player@example.com' }] }] };
    const envelope = await createWebsiteEnvelope(source);
    const save = await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope }) });
    assert.equal(save.status, 200);
    const response = await fetch(`${base}/api/vault`, { headers });
    const remote = await response.json();
    assert.deepEqual(await decryptWebsiteEnvelope(remote.envelope), source);
    assert.equal(JSON.stringify(remote).includes('player@example.com'), false);
    assert.equal(JSON.stringify(remote.envelope).includes('player@example.com'), false);
  } finally {
    await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  }
});
