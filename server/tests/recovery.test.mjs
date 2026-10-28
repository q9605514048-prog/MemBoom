import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRecoveryKey, protectVaultKey, recoverVaultKey } from '../src/recovery.mjs';

test('recovery key can protect and recover a vault key', async () => {
  const vaultKey = new Uint8Array([1, 2, 3, 4, 5, 6]);
  const recovery = await createRecoveryKey();
  const wrapped = await protectVaultKey(vaultKey, recovery.key);
  assert.notEqual(wrapped.ciphertext, undefined);
  assert.deepEqual(await recoverVaultKey(wrapped, recovery.key), vaultKey);
});

test('wrong recovery key cannot recover the vault key', async () => {
  const recovery = await createRecoveryKey();
  const wrapped = await protectVaultKey(new Uint8Array([9, 8, 7]), recovery.key);
  const other = await createRecoveryKey();
  await assert.rejects(() => recoverVaultKey(wrapped, other.key));
});
