import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import {webcrypto} from 'node:crypto';
const source=await readFile(new URL('../scripts/notes-model.js',import.meta.url),'utf8');
const context=vm.createContext({crypto:webcrypto});vm.runInContext(source,context);const model=context.MemBoomNotes;
const note={Id:'qa',Title:'Идеи',Content:'Многострочный\nТЕКСТ',Pinned:true,UpdatedAt:'2026-10-26T00:00:00+00:00'};
test('Windows and Android full backups restore separate notes',()=>{
  assert.equal(model.parse({categories:[],notes:[note]}).notes[0].Content,note.Content);
  assert.equal(model.parse([]).notes.length,0);
});
test('actual encrypted Windows notes backup opens in website format',async()=>{
  const raw=await readFile(new URL('../../clients/android/app/src/test/resources/windows-notes-v1.json',import.meta.url),'utf8');
  const envelope=JSON.parse(raw),salt=Buffer.from(envelope.salt,'base64'),nonce=Buffer.from(envelope.nonce,'base64');
  const material=await webcrypto.subtle.importKey('raw',new TextEncoder().encode('QaBackup!123'),'PBKDF2',false,['deriveKey']);
  const key=await webcrypto.subtle.deriveKey({name:'PBKDF2',salt,iterations:310000,hash:'SHA-256'},material,{name:'AES-GCM',length:256},false,['decrypt']);
  const clear=await webcrypto.subtle.decrypt({name:'AES-GCM',iv:nonce},key,Buffer.from(envelope.ciphertext,'base64'));
  const state=model.parse(JSON.parse(new TextDecoder().decode(clear)));
  assert.equal(state.notes[0].Title,'Общая заметка');assert.equal(state.notes[0].Pinned,true);assert.equal(state.categories[0].accounts.length,1);
});
test('note search and pin ordering',()=>{
  assert.equal(model.search([note],'текст').length,1);
  assert.equal(model.search([note,{...note,Id:'other',Pinned:false,UpdatedAt:'2026-10-27T00:00:00Z'}],'')[0].Id,'qa');
});
test('conflicting note imports preserve both and are idempotent',()=>{
  const incoming=[{...note,Content:'Другая версия'}];const merged=model.merge([note],incoming);
  assert.equal(merged.length,2);assert.notEqual(merged[0].Id,merged[1].Id);assert.equal(model.merge(merged,incoming).length,2);
});
test('malformed notes are rejected before import',()=>{
  for(const notes of [[{...note,Title:' '}],[note,note],[{...note,Content:null}],[{...note,UpdatedAt:'not-date'}]])assert.throws(()=>model.validate(notes));
});
test('website no longer loads character or animation controls',async()=>{
  const html=await readFile(new URL('../index.html',import.meta.url),'utf8');
  assert.doesNotMatch(html,/chibi|mascotMotion|megumin-walk|megumin-chibi/);
});
