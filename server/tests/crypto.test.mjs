import { test } from 'node:test';
import assert from 'node:assert/strict';
import { encryptJson, decryptJson } from '../src/crypto.mjs';

test('accepts a four-character ASCII master password with symbols', async () => {
  const envelope = await encryptJson({ ok: true }, 'a1!b');
  assert.deepEqual(await decryptJson(envelope, 'a1!b'), { ok: true });
});

test('rejects short, whitespace, and non-ASCII master passwords', async () => {
  await assert.rejects(() => encryptJson({}, 'a1!'));
  await assert.rejects(() => encryptJson({}, 'ab c'));
  await assert.rejects(() => encryptJson({}, 'пароль'));
  const envelope = await encryptJson({}, 'a1!b');
  await assert.rejects(() => decryptJson(envelope, 'a1!'));
});

test('encrypts and decrypts JSON with a master password', async () => {
  const source = { categories: [{ name: 'Demo', fields: [{ label: 'Login', value: 'example' }] }] };
  const envelope = await encryptJson(source, 'correctHorse!1');
  assert.equal(typeof envelope.ciphertext, 'string');
  assert.equal(envelope.version, 1);
  assert.notEqual(envelope.ciphertext.includes('example'), true);
  assert.deepEqual(await decryptJson(envelope, 'correctHorse!1'), source);
});

test('rejects a wrong master password', async () => {
  const envelope = await encryptJson({ secret: 'value' }, 'rightPass!1');
  await assert.rejects(() => decryptJson(envelope, 'wrongPass!1'));
});

test('uses a fresh salt and nonce for every encryption', async () => {
  const first = await encryptJson({ value: 1 }, 'password');
  const second = await encryptJson({ value: 1 }, 'password');
  assert.notEqual(first.salt, second.salt);
  assert.notEqual(first.nonce, second.nonce);
  assert.notEqual(first.ciphertext, second.ciphertext);
});
