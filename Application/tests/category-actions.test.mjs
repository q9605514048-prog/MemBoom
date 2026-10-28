import { test } from 'node:test';
import assert from 'node:assert/strict';
import { openVault } from './helpers/vault-harness.mjs';

test('category button opens a form and saves a category that survives reopening', async () => {
  const app = await openVault();
  app.get('#addCategory').onclick();
  assert.match(app.get('#modal').innerHTML, /Новая категория/);
  await app.submit('  Игры  ');
  assert.match(app.get('#list').innerHTML, /Игры/);
  assert.equal(app.get('#modal').innerHTML, '');
  const reopened = await openVault(app.storage);
  assert.match(reopened.get('#list').innerHTML, /Игры/);
});

test('category form rejects whitespace and supports cancelling', async () => {
  const app = await openVault();
  app.get('#addCategory').onclick();
  await app.submit('   ');
  assert.doesNotMatch(app.get('#list').innerHTML, /data-category=/);
  assert.notEqual(app.get('#modal').innerHTML, '');
  app.cancel.onclick();
  assert.equal(app.get('#modal').innerHTML, '');
});

test('rename action uses the same working form and keeps one category', async () => {
  const app = await openVault();
  app.get('#addCategory').onclick();
  await app.submit('Игры');
  const id = app.get('#list').innerHTML.match(/data-category="([^"]+)"/)[1];
  const action = { dataset: { action: 'edit-category', id } };
  await app.get('#list').listeners.click({ target: { closest: selector => selector === '[data-action]' ? action : null } });
  assert.match(app.get('#modal').innerHTML, /Переименовать категорию/);
  assert.match(app.get('#modal').innerHTML, /value="Игры"/);
  await app.submit('Работа');
  assert.match(app.get('#list').innerHTML, /Работа/);
  assert.doesNotMatch(app.get('#list').innerHTML, /Игры/);
  assert.equal((app.get('#list').innerHTML.match(/<article/g) || []).length, 1);
});
