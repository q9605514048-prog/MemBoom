import {test} from 'node:test';
import assert from 'node:assert/strict';
import {openVault} from './helpers/vault-harness.mjs';

test('notes save encrypted, survive reopening, search, pin and delete',async()=>{
  const app=await openVault();app.get('#notesTab').onclick();assert.equal(app.get('#accountsPanel').hidden,true);
  app.get('#newNote').onclick();await app.submitNote('Идея','Текст для поиска',true);
  assert.equal(app.get('#modal').innerHTML,'');assert.match(app.get('#notesList').innerHTML,/Идея/);
  assert.ok(!app.storage.get('vault-encrypted').includes('Текст для поиска'));
  const reopened=await openVault(app.storage);assert.match(reopened.get('#notesList').innerHTML,/Идея/);
  reopened.get('#notesSearch').value='ПОИСКА';reopened.get('#notesSearch').listeners.input();assert.match(reopened.get('#notesList').innerHTML,/Идея/);
  const id=reopened.get('#notesList').innerHTML.match(/data-id="([^"]+)"/)[1];
  await reopened.clickNote({noteAction:'pin',id});assert.match(reopened.get('#notesList').innerHTML,/aria-pressed="false"/);
  await reopened.clickNote({noteAction:'delete',id});assert.doesNotMatch(reopened.get('#notesList').innerHTML,/Текст для поиска/);
  const deleted=await openVault(app.storage);assert.equal(deleted.get('#notesCount').textContent,'Заметок: 0 из 0');
});
test('note text is escaped and cleared when locked',async()=>{
  const app=await openVault();app.get('#newNote').onclick();await app.submitNote('<img src=x onerror=alert(1)>','<script>private</script>');
  assert.doesNotMatch(app.get('#notesList').innerHTML,/<script>|<img src=x/);
  app.get('#lockBtn').listeners.click();assert.doesNotMatch(app.get('#notesList').innerHTML,/private/);
});
test('editing a note keeps its id and does not change account categories',async()=>{
  const app=await openVault();app.get('#addCategory').onclick();await app.submit('Игры');
  app.get('#newNote').onclick();await app.submitNote('Название','Первая версия');
  const id=app.get('#notesList').innerHTML.match(/data-id="([^"]+)"/)[1];
  await app.clickNote({noteAction:'edit',id});await app.submitNote('Изменённая','Вторая версия');
  assert.match(app.get('#list').innerHTML,/Игры/);assert.equal((app.get('#notesList').innerHTML.match(/<article/g)||[]).length,1);
  assert.ok(app.get('#notesList').innerHTML.includes(`data-id="${id}"`));
});

test('cloud file shortcuts are encrypted, survive editing and reopening, and clear on lock',async()=>{
 const app=await openVault(),links=[{Name:'Договор <черновик>',Url:'https://drive.google.com/file/d/private-file/view?resourcekey=private-key'}];
 app.get('#newNote').onclick();await app.submitNote('Рабочие файлы','Открыть документ',false,links);
 assert.match(app.get('#notesList').innerHTML,/Договор &lt;черновик&gt;/);assert.match(app.get('#notesList').innerHTML,/rel="noopener noreferrer"/);
 assert.doesNotMatch(app.storage.get('vault-encrypted'),/private-file|private-key|Договор/);
 const reopened=await openVault(app.storage),id=reopened.get('#notesList').innerHTML.match(/data-id="([^"]+)"/)[1];
 await reopened.clickNote({noteAction:'edit',id});await reopened.submitNote('Рабочие файлы','Новый текст');assert.match(reopened.get('#notesList').innerHTML,/private-file/);
 reopened.get('#notesSearch').value='договор';reopened.get('#notesSearch').listeners.input();assert.match(reopened.get('#notesList').innerHTML,/Новый текст/);
 reopened.get('#lockBtn').listeners.click();assert.doesNotMatch(reopened.get('#notesList').innerHTML,/private-file|Договор/);
});

test('failed file-link save keeps the previous encrypted note',async()=>{
 class Storage extends Map{set(key,value){if(this.fail&&key==='vault-encrypted'){this.fail=false;throw Error('quota');}return super.set(key,value);}}
 const storage=new Storage(),app=await openVault(storage);app.get('#newNote').onclick();await app.submitNote('Файл','Текст',false,[{Name:'Первый',Url:'https://disk.yandex.ru/d/first'}]);
 const id=app.get('#notesList').innerHTML.match(/data-id="([^"]+)"/)[1];await app.clickNote({noteAction:'edit',id});storage.fail=true;
 await app.submitNote('Файл','Текст',false,[{Name:'Второй',Url:'https://disk.yandex.ru/d/second'}]);
 const reopened=await openVault(storage);assert.match(reopened.get('#notesList').innerHTML,/\/d\/first/);assert.doesNotMatch(reopened.get('#notesList').innerHTML,/\/d\/second/);
});
