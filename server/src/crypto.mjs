import { webcrypto } from 'node:crypto';

const { subtle } = webcrypto;
const encoder = new TextEncoder();
const decoder = new TextDecoder();
const ITERATIONS = 310_000;
const KEY_LENGTH = 256;
const MASTER_PASSWORD_PATTERN = /^[\x21-\x7E]{4,}$/;

export function isValidMasterPassword(password) {
  return typeof password === 'string' && MASTER_PASSWORD_PATTERN.test(password);
}

function bytesToBase64(bytes) {
  return Buffer.from(bytes).toString('base64url');
}

function base64ToBytes(value) {
  return new Uint8Array(Buffer.from(value, 'base64url'));
}

async function deriveKey(password, salt) {
  const material = await subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']);
  return subtle.deriveKey({ name: 'PBKDF2', salt, iterations: ITERATIONS, hash: 'SHA-256' }, material, { name: 'AES-GCM', length: KEY_LENGTH }, false, ['encrypt', 'decrypt']);
}

export async function encryptJson(value, password) {
  if (!isValidMasterPassword(password)) throw new Error('Master password must contain at least 4 ASCII letters, digits, or symbols');
  const salt = webcrypto.getRandomValues(new Uint8Array(16));
  const nonce = webcrypto.getRandomValues(new Uint8Array(12));
  const key = await deriveKey(password, salt);
  const plaintext = encoder.encode(JSON.stringify(value));
  const ciphertext = await subtle.encrypt({ name: 'AES-GCM', iv: nonce }, key, plaintext);
  return { version: 1, kdf: 'PBKDF2-SHA-256', iterations: ITERATIONS, cipher: 'AES-256-GCM', salt: bytesToBase64(salt), nonce: bytesToBase64(nonce), ciphertext: bytesToBase64(new Uint8Array(ciphertext)) };
}

export async function decryptJson(envelope, password) {
  if (!isValidMasterPassword(password)) throw new Error('Master password must contain at least 4 ASCII letters, digits, or symbols');
  if (!envelope || envelope.version !== 1 || envelope.kdf !== 'PBKDF2-SHA-256' || envelope.cipher !== 'AES-256-GCM') throw new Error('Unsupported encryption envelope');
  const salt = base64ToBytes(envelope.salt);
  const nonce = base64ToBytes(envelope.nonce);
  const ciphertext = base64ToBytes(envelope.ciphertext);
  const key = await deriveKey(password, salt);
  const plaintext = await subtle.decrypt({ name: 'AES-GCM', iv: nonce }, key, ciphertext);
  return JSON.parse(decoder.decode(plaintext));
}
