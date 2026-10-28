import { test } from 'node:test';
import assert from 'node:assert/strict';
import { openVault } from './helpers/vault-harness.mjs';

const seed = [
  { id: 'games', name: 'Игры', accounts: [
    { id: 'steam', name: 'Steam', fields: [{ label: 'Почта', value: 'player@example.test' }] },
    { id: 'epic', name: 'Epic', favorite: true, fields: [] }
  ] },
  { id: 'work', name: 'Работа', accounts: [{ id: 'mail', name: 'Почта', favorite: true, fields: [] }] },
  { id: 'empty', name: 'Пустая', accounts: [] }
];
const setup = () => openVault(new Map([['my-info-v1', JSON.stringify(seed)]]));
const change = (app, id, value) => { app.get(id).value = value; app.get(id).listeners.change(); };

test('category and text search combine, including custom field values', async () => {
  const app = await setup();
  change(app, '#categoryFilter', 'games');
  app.get('#search').value = 'PLAYER@EXAMPLE';
  app.get('#search').listeners.input();
  assert.match(app.get('#list').innerHTML, /Steam/);
  assert.doesNotMatch(app.get('#list').innerHTML, /Epic|Работа/);
  assert.equal(app.get('#resultsCount').textContent, 'Показано аккаунтов: 1 из 3');
});

test('matching category names do not bypass the favorites filter', async () => {
  const app = await setup();
  app.get('#favoritesFilter').onclick();
  app.get('#search').value = 'Игры';
  app.get('#search').listeners.input();
  assert.match(app.get('#list').innerHTML, /Epic/);
  assert.doesNotMatch(app.get('#list').innerHTML, /Steam|Работа/);
});

test('favorites toggle survives reopening and can be removed from a filtered view', async () => {
  const app = await setup();
  await app.clickAction({ action: 'toggle-favorite', category: 'games', id: 'steam' });
  const reopened = await openVault(app.storage);
  reopened.get('#favoritesFilter').onclick();
  assert.match(reopened.get('#list').innerHTML, /Steam/);
  await reopened.clickAction({ action: 'toggle-favorite', category: 'games', id: 'steam' });
  assert.doesNotMatch(reopened.get('#list').innerHTML, /Steam/);
  assert.match(reopened.get('#list').innerHTML, /Epic/);
  assert.ok(!app.storage.has('my-info-v1'));
  assert.ok(!app.storage.get('vault-encrypted').includes('Steam'));
});

test('sorting is reversible and does not alter saved account order', async () => {
  const app = await setup();
  change(app, '#sortOrder', 'name-asc');
  let content = app.get('#list').innerHTML;
  assert.ok(content.indexOf('Epic') < content.indexOf('Steam'));
  change(app, '#sortOrder', 'name-desc');
  content = app.get('#list').innerHTML;
  assert.ok(content.indexOf('Steam') < content.indexOf('Epic'));
  await app.clickAction({ action: 'toggle-favorite', category: 'games', id: 'steam' });
  const reopened = await openVault(app.storage);
  content = reopened.get('#list').innerHTML;
  assert.ok(content.indexOf('Steam') < content.indexOf('Epic'));
});

test('empty results offer a reset that restores categories and counts', async () => {
  const app = await setup();
  change(app, '#categoryFilter', 'empty');
  app.get('#favoritesFilter').onclick();
  assert.match(app.get('#list').innerHTML, /По выбранным условиям ничего не найдено/);
  app.get('#resetFilters').onclick();
  assert.match(app.get('#list').innerHTML, /Steam/);
  assert.match(app.get('#list').innerHTML, /Пустая/);
  assert.equal(app.get('#categoryFilter').value, '');
  assert.equal(app.get('#favoritesFilter').getAttribute('aria-pressed'), 'false');
});

test('blocking the vault clears the filter state and category names', async () => {
  const app = await setup();
  change(app, '#categoryFilter', 'games');
  app.get('#favoritesFilter').onclick();
  app.get('#search').value = 'Epic';
  app.get('#lockBtn').listeners.click();
  assert.equal(app.get('#search').value, '');
  assert.equal(app.get('#favoritesFilter').getAttribute('aria-pressed'), 'false');
  assert.doesNotMatch(app.get('#categoryFilter').innerHTML, /Игры|Работа/);
  assert.doesNotMatch(app.get('#list').innerHTML, /Epic|Steam/);
});

test('unknown category selection falls back to all categories', async () => {
  const app = await setup();
  change(app, '#categoryFilter', 'removed-category');
  assert.equal(app.get('#categoryFilter').value, '');
  assert.match(app.get('#list').innerHTML, /Игры/);
  assert.match(app.get('#list').innerHTML, /Работа/);
});

test('failed favorite writes keep the existing saved state and allow retry', async () => {
  const app = await setup();
  const previous = app.storage.get('vault-encrypted');
  app.storage.set = () => { throw new Error('Storage full'); };
  await app.clickAction({ action: 'toggle-favorite', category: 'games', id: 'steam' });
  assert.equal(app.storage.get('vault-encrypted'), previous);
  assert.match(app.get('#toast').textContent, /Не удалось сохранить/);
  delete app.storage.set;
  await app.clickAction({ action: 'toggle-favorite', category: 'games', id: 'steam' });
  const reopened = await openVault(app.storage);
  reopened.get('#favoritesFilter').onclick();
  assert.match(reopened.get('#list').innerHTML, /Steam/);
});
