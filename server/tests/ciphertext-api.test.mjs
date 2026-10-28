import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from '../src/server.mjs';

async function withServer(run) {
  const server = createServer({ token: 'test-token-123456' });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  try { await run(`http://127.0.0.1:${address.port}`); }
  finally { await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())); }
}

test('creates the storage directory and writes only the encrypted envelope', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'vault-test-'));
  const file = join(directory, 'nested', 'vault.json');
  const server = createServer({ token: 'test-token-123456', storagePath: file });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const headers = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    await fetch(`http://127.0.0.1:${server.address().port}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'ciphertext-only' } }) });
    assert.equal(existsSync(file), true);
    const stored = readFileSync(file, 'utf8');
    assert.equal(stored.includes('ciphertext-only'), true);
    assert.equal(stored.includes('password'), false);
  } finally {
    await new Promise(resolve => server.close(resolve));
    rmSync(directory, { recursive: true, force: true });
  }
});

test('persists the encrypted envelope and revision through server restart', async () => {
  const file = fileURLToPath(new URL('../tmp-vault-test.json', import.meta.url));
  const firstServer = createServer({ token: 'test-token-123456', storagePath: file });
  await new Promise(resolve => firstServer.listen(0, '127.0.0.1', resolve));
  const address = firstServer.address();
  const headers = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
  await fetch(`http://127.0.0.1:${address.port}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'persisted-only' } }) });
  await new Promise(resolve => firstServer.close(resolve));
  const secondServer = createServer({ token: 'test-token-123456', storagePath: file });
  await new Promise(resolve => secondServer.listen(0, '127.0.0.1', resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${secondServer.address().port}/api/vault`, { headers: { authorization: headers.authorization } });
    const result = await response.json();
    assert.equal(result.revision, 1);
    assert.equal(result.envelope.ciphertext, 'persisted-only');
  } finally {
    await new Promise(resolve => secondServer.close(resolve));
    rmSync(file, { force: true });
  }
});

test('registers a device and allows it to access the vault', async () => {
  await withServer(async base => {
    const admin = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    const registration = await fetch(`${base}/api/devices`, { method: 'POST', headers: admin, body: JSON.stringify({ name: 'Android phone' }) });
    assert.equal(registration.status, 201);
    const device = await registration.json();
    assert.equal(typeof device.deviceId, 'string');
    assert.equal(typeof device.deviceToken, 'string');
    const response = await fetch(`${base}/api/vault`, { headers: { authorization: `Bearer ${device.deviceToken}` } });
    assert.equal(response.status, 200);
  });
});

test('lists devices without returning device tokens', async () => {
  await withServer(async base => {
    const admin = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    const registration = await fetch(`${base}/api/devices`, { method: 'POST', headers: admin, body: JSON.stringify({ name: 'Tablet' }) });
    const created = await registration.json();
    const response = await fetch(`${base}/api/devices`, { headers: { authorization: admin.authorization } });
    assert.equal(response.status, 200);
    const devices = await response.json();
    assert.equal(devices.length, 1);
    assert.equal(devices[0].deviceId, created.deviceId);
    assert.equal(Object.hasOwn(devices[0], 'deviceToken'), false);
  });
});

test('revoked devices cannot access the vault', async () => {
  await withServer(async base => {
    const admin = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    const registration = await fetch(`${base}/api/devices`, { method: 'POST', headers: admin, body: JSON.stringify({ name: 'Lost phone' }) });
    const device = await registration.json();
    const revoke = await fetch(`${base}/api/devices/${device.deviceId}/revoke`, { method: 'POST', headers: admin });
    assert.equal(revoke.status, 200);
    const response = await fetch(`${base}/api/vault`, { headers: { authorization: `Bearer ${device.deviceToken}` } });
    assert.equal(response.status, 401);
  });
});

test('device registrations survive server restart', async () => {
  const file = fileURLToPath(new URL('../tmp-device-test.json', import.meta.url));
  const first = createServer({ token: 'test-token-123456', storagePath: file });
  await new Promise(resolve => first.listen(0, '127.0.0.1', resolve));
  const admin = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
  let device;
  try {
    const registration = await fetch(`http://127.0.0.1:${first.address().port}/api/devices`, { method: 'POST', headers: admin, body: JSON.stringify({ name: 'Desktop' }) });
    device = await registration.json();
  } finally { await new Promise(resolve => first.close(resolve)); }
  const second = createServer({ token: 'test-token-123456', storagePath: file });
  await new Promise(resolve => second.listen(0, '127.0.0.1', resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${second.address().port}/api/vault`, { headers: { authorization: `Bearer ${device.deviceToken}` } });
    assert.equal(response.status, 200);
  } finally {
    await new Promise(resolve => second.close(resolve));
    rmSync(file, { force: true });
  }
});

test('keeps the default server bind address local-only', async () => {
  const source = readFileSync(new URL('../src/server.mjs', import.meta.url), 'utf8');
  assert.match(source, /VAULT_HOST/);
  assert.match(source, /127\.0\.0\.1/);
});

test('reports a health status without exposing vault data', async () => {
  await withServer(async base => {
    const response = await fetch(`${base}/health`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { status: 'ok' });
  });
});

test('temporarily rate-limits repeated unauthorized vault requests', async () => {
  const server = createServer({ token: 'test-token-123456', maxAuthFailures: 2, authBlockMs: 50 });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const request = () => fetch(`${base}/api/vault`, { headers: { authorization: 'Bearer wrong-token-123456' } });
    assert.equal((await request()).status, 401);
    assert.equal((await request()).status, 429);
    assert.equal((await request()).status, 429);
    await new Promise(resolve => setTimeout(resolve, 70));
    assert.equal((await request()).status, 401);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});

test('rejects vault access without bearer authentication', async () => {
  await withServer(async base => {
    const response = await fetch(`${base}/api/vault`);
    assert.equal(response.status, 401);
  });
});

test('stores a client ciphertext envelope without exposing the client model', async () => {
  await withServer(async base => {
    const headers = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    const envelope = { version: 1, salt: 'salt', nonce: 'nonce', ciphertext: 'opaque-ciphertext' };
    const response = await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope }) });
    assert.equal(response.status, 200);
    const current = await fetch(`${base}/api/vault`, { headers: { authorization: headers.authorization } });
    const result = await current.json();
    assert.deepEqual(result.envelope, envelope);
    assert.equal(JSON.stringify(result).includes('example-password'), false);
  });
});

test('accepts encrypted envelope and increments revision using compare-and-swap', async () => {
  await withServer(async base => {
    const headers = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    const first = await fetch(`${base}/api/vault`, { headers: { authorization: headers.authorization } });
    assert.equal(first.status, 200);
    assert.equal((await first.json()).revision, 0);
    const save = await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'ciphertext-only' } }) });
    assert.equal(save.status, 200);
    assert.equal((await save.json()).revision, 1);
    const stale = await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'stale' } }) });
    assert.equal(stale.status, 409);
    assert.equal((await stale.json()).revision, 1);
  });
});

test('preserves a stale encrypted version as a conflict', async () => {
  await withServer(async base => {
    const headers = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'first' } }) });
    const response = await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'offline-version' } }) });
    assert.equal(response.status, 409);
    const result = await response.json();
    assert.equal(result.error, 'revision_conflict');
    assert.equal(result.conflict.envelope.ciphertext, 'offline-version');
    const current = await fetch(`${base}/api/vault`, { headers: { authorization: headers.authorization } });
    const currentResult = await current.json();
    assert.equal(currentResult.conflicts.length, 1);
    assert.equal(currentResult.conflicts[0].envelope.ciphertext, 'offline-version');
  });
});

test('persists conflicts through server restart until they are resolved', async () => {
  const file = fileURLToPath(new URL('../tmp-conflict-test.json', import.meta.url));
  const firstServer = createServer({ token: 'test-token-123456', storagePath: file });
  await new Promise(resolve => firstServer.listen(0, '127.0.0.1', resolve));
  const headers = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
  try {
    const base = `http://127.0.0.1:${firstServer.address().port}`;
    await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'current' } }) });
    await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'offline' } }) });
  } finally { await new Promise(resolve => firstServer.close(resolve)); }
  const secondServer = createServer({ token: 'test-token-123456', storagePath: file });
  await new Promise(resolve => secondServer.listen(0, '127.0.0.1', resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${secondServer.address().port}/api/vault`, { headers: { authorization: headers.authorization } });
    const result = await response.json();
    assert.equal(result.conflicts.length, 1);
    assert.equal(result.conflicts[0].envelope.ciphertext, 'offline');
  } finally {
    await new Promise(resolve => secondServer.close(resolve));
    rmSync(file, { force: true });
  }
});

test('two devices can create and resolve a stale encrypted snapshot conflict', async () => {
  await withServer(async base => {
    const adminHeaders = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    const register = async name => {
      const response = await fetch(`${base}/api/devices`, { method: 'POST', headers: adminHeaders, body: JSON.stringify({ name }) });
      return (await response.json()).deviceToken;
    };
    const firstToken = await register('Android');
    const secondToken = await register('Web');
    const firstHeaders = { authorization: `Bearer ${firstToken}`, 'content-type': 'application/json' };
    const secondHeaders = { authorization: `Bearer ${secondToken}`, 'content-type': 'application/json' };

    const initial = await fetch(`${base}/api/vault`, { headers: firstHeaders });
    const initialRevision = (await initial.json()).revision;
    assert.equal((await fetch(`${base}/api/vault`, { method: 'PUT', headers: firstHeaders, body: JSON.stringify({ revision: initialRevision, envelope: { version: 1, salt: 's1', nonce: 'n1', ciphertext: 'first-device' } }) })).status, 200);

    const staleWrite = await fetch(`${base}/api/vault`, { method: 'PUT', headers: secondHeaders, body: JSON.stringify({ revision: initialRevision, envelope: { version: 1, salt: 's2', nonce: 'n2', ciphertext: 'second-device' } }) });
    assert.equal(staleWrite.status, 409);
    const conflict = (await staleWrite.json()).conflict;
    const visible = await fetch(`${base}/api/vault`, { headers: secondHeaders });
    const snapshot = await visible.json();
    assert.equal(snapshot.envelope.ciphertext, 'first-device');
    assert.equal(snapshot.conflicts.length, 1);
    assert.equal(snapshot.conflicts[0].id, conflict.id);

    const resolved = await fetch(`${base}/api/vault/resolve`, { method: 'POST', headers: secondHeaders, body: JSON.stringify({ conflictId: conflict.id }) });
    assert.equal(resolved.status, 200);
    const after = await fetch(`${base}/api/vault`, { headers: firstHeaders });
    const finalSnapshot = await after.json();
    assert.equal(finalSnapshot.envelope.ciphertext, 'second-device');
    assert.equal(finalSnapshot.conflicts.length, 0);
  });
});

test('resolves a stored conflict by selecting the whole encrypted envelope', async () => {
  await withServer(async base => {
    const headers = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'current' } }) });
    const conflictResponse = await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'selected' } }) });
    const conflict = (await conflictResponse.json()).conflict;
    const resolveResponse = await fetch(`${base}/api/vault/resolve`, { method: 'POST', headers, body: JSON.stringify({ conflictId: conflict.id }) });
    assert.equal(resolveResponse.status, 200);
    assert.equal((await resolveResponse.json()).revision, 2);
    const current = await fetch(`${base}/api/vault`, { headers: { authorization: headers.authorization } });
    const result = await current.json();
    assert.equal(result.envelope.ciphertext, 'selected');
    assert.equal(result.conflicts.length, 0);
  });
});

test('retains the current envelope when a write has a stale revision', async () => {
  await withServer(async base => {
    const headers = { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' };
    await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'first' } }) });
    const response = await fetch(`${base}/api/vault`, { method: 'PUT', headers, body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'stale' } }) });
    const result = await response.json();
    assert.equal(result.envelope.ciphertext, 'first');
    const current = await fetch(`${base}/api/vault`, { headers: { authorization: headers.authorization } });
    assert.equal((await current.json()).envelope.ciphertext, 'first');
  });
});

test('rejects cleartext secret fields nested inside an envelope', async () => {
  await withServer(async base => {
    const response = await fetch(`${base}/api/vault`, {
      method: 'PUT',
      headers: { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' },
      body: JSON.stringify({ revision: 0, envelope: { version: 1, ciphertext: 'ciphertext-only', metadata: { password: 'must-not-be-sent' } } })
    });
    assert.equal(response.status, 400);
  });
});

test('rejects payloads containing cleartext secret fields', async () => {
  await withServer(async base => {
    const response = await fetch(`${base}/api/vault`, {
      method: 'PUT',
      headers: { authorization: 'Bearer test-token-123456', 'content-type': 'application/json' },
      body: JSON.stringify({ revision: 0, password: 'should-not-be-sent' })
    });
    assert.equal(response.status, 400);
  });
});
