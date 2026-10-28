import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import {openVault} from './helpers/vault-harness.mjs';
const messages=JSON.parse(await readFile(new URL('../../locales/en.json',import.meta.url),'utf8'));
const core=await readFile(new URL('../scripts/i18n-core.js',import.meta.url),'utf8');
function fixture(language='en',stored=null){
 const data=new Map(stored?[['memboom.language.v1',stored]]:[]);let reloads=0;
 const context=vm.createContext({MemBoomTranslations:messages,navigator:{language},localStorage:{getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v)},location:{reload(){reloads++}},document:{readyState:'loading',addEventListener(){}}});
 vm.runInContext(core,context);return {i18n:context.MemBoomI18n,data,reloads:()=>reloads};
}
test('language selection overrides system language and is saved without vault data',()=>{
 const f=fixture('en-US','ru');assert.equal(f.i18n.language,'ru');assert.equal(f.i18n.t('Заметки'),'Заметки');
 f.i18n.select('en');assert.equal(f.data.get('memboom.language.v1'),'en');assert.equal(f.reloads(),1);f.i18n.select('invalid');assert.equal(f.reloads(),1);
});
test('English messages preserve supplied user text and template parameters',()=>{
 const {i18n}=fixture();assert.equal(i18n.t('Заметки'),'Notes');assert.equal(i18n.t('Аккаунт {0}','Удалить'),'Account Удалить');
 assert.equal(i18n.t('Заметок: {0} из {1}',2,5),'Notes: 2 of 5');
 for(const [key,value] of Object.entries(messages)){const slots=v=>[...v.matchAll(/\{\d+\}/g)].map(x=>x[0]).sort();assert.deepEqual(slots(key),slots(value),key);}
});
test('changing UI language does not translate, discard or expose saved notes',async()=>{
 const storage=new Map(),en=fixture().i18n;const app=await openVault(storage,{MemBoomI18n:en});
 app.get('#newNote').onclick();await app.submitNote('Новая заметка','Удалить');assert.match(app.get('#notesList').innerHTML,/Новая заметка/);assert.match(app.get('#notesList').innerHTML,/Удалить/);assert.match(app.get('#notesList').innerHTML,/Edit/);
 assert.ok(!storage.get('vault-encrypted').includes('Удалить'));
 const ru=await openVault(storage);assert.match(ru.get('#notesList').innerHTML,/Новая заметка/);assert.match(ru.get('#notesList').innerHTML,/Удалить/);
 assert.equal(en.t('Лецуха'),'Yuri');
});
