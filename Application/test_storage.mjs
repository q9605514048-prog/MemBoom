import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');

test('app contains category and account management with copy controls', () => {
  assert.match(html, /id="addCategory"/);
  assert.match(html, /data-action="add-account"/);
  assert.match(html, /data-copy/);
});

test('app persists data locally and provides search and export', () => {
  assert.match(html, /localStorage/);
  assert.match(html, /type="search"/);
  assert.match(html, /Экспорт/);
});

test('app supports custom named fields and old record migration', () => {
  assert.match(html, /label:\s*'Почта'/);
  assert.match(html, /label:\s*'Пароль'/);
  assert.match(html, /data-action="add-field"/);
  assert.match(html, /data-copy="\$\{esc\(f\.value\)\}"/);
  assert.match(html, /const \{login,password,note,\.\.\.rest\}=account/);
  assert.match(html, /data\.map\(cat=>\(\{\.\.\.cat,accounts:\(cat\.accounts\|\|\[\]\)\.map\(migrateAccount\)\}\)\)/);
});

test('app has no hard-coded default accounts', () => {
  assert.match(html, /if \(!Array\.isArray\(data\)\) data = \[\];/);
});

test('app includes a master password lock and encrypted local storage hooks', () => {
  assert.match(html, /Мастер-пароль/);
  assert.match(html, /crypto\.subtle/);
  assert.match(html, /vault-encrypted/);
  assert.match(html, /Заблокировать/);
  assert.match(html, /minlength="4"/);
  assert.match(html, /MASTER_PASSWORD_PATTERN/);
  assert.match(html, /Английские буквы, цифры и спецсимволы/);
});

test('app exports and imports encrypted backups instead of cleartext JSON', () => {
  assert.match(html, /Зашифрованная резервная копия/);
  assert.match(html, /const envelope=await encryptLocal\(includeNotes\?\{categories:data,notes,library\}:data,password\)/);
  assert.match(html, /decryptLocal\(await file\.text\(\), password\)/);
  assert.doesNotMatch(html, /JSON\.stringify\(data,null,2\)/);
});

test('app supports inactivity and page-hide auto-locking', () => {
  assert.match(html, /AUTO_LOCK_MS/);
  assert.match(html, /visibilitychange/);
  assert.match(html, /setTimeout\(lockApp/);
});

test('app does not upload local data when server-version replacement is declined', () => {
  assert.match(html, /if\(!confirm\('Заменить локальные данные серверной версией\?'\)\)return;/);
});

test('app has optional ciphertext synchronization without sending cleartext data', () => {
  assert.match(html, /Синхронизация/);
  assert.match(html, /\/api\/vault/);
  assert.match(html, /syncVault/);
  assert.match(html, /envelope/);
  assert.doesNotMatch(html, /body:\s*JSON\.stringify\(data\)/);
});

test('app exposes the Megumin theme contract without changing vault behavior', () => {
  assert.match(html, /data-theme="megumin"/);
  assert.match(html, /--color-magic-red/);
  assert.match(html, /--color-magic-gold/);
  assert.match(html, /Мегумин/);
});

test('app provides separate notes without a character overlay', () => {
  assert.match(html, /id="notesPanel"/);
  assert.doesNotMatch(html, /chibi|mascotMotion/);
});

test('app provides a local password generator in account forms', () => {
  assert.match(html, /generatePassword/);
  assert.match(html, /crypto\.getRandomValues/);
  assert.match(html, /data-generate-password/);
  assert.match(html, /20/);
});
