import {test} from 'node:test';import assert from 'node:assert/strict';
import {openVault} from './helpers/vault-harness.mjs';
const row={title:'Личная коллекция',url:'https://yummyanime.tv/123-test.html',status:'active',progress:3};
test('library persists encrypted, survives reopening, keeps progress on repeated import and clears on lock',async()=>{
 const a=await openVault();assert.equal(await a.importLibrary([row]),true);assert.ok(!a.storage.get('vault-encrypted').includes(row.title));
 const b=await openVault(a.storage);assert.equal(b.getLibrary().length,1);assert.equal(b.getLibrary()[0].Progress,3);
 await b.changeLibrary(items=>{items[0].Progress=4;});await b.importLibrary([row]);assert.equal(b.getLibrary().length,1);assert.equal(b.getLibrary()[0].Progress,4);
 b.get('#lockBtn').listeners.click();assert.equal(b.getLibrary().length,0);
});
test('failed library storage rolls back and preserves accounts and notes',async()=>{
 const a=await openVault();a.get('#addCategory').onclick();await a.submit('Игры');a.get('#newNote').onclick();await a.submitNote('Заметка','Сохранённый текст');
 const original=a.storage.set;a.storage.set=()=>{throw Error('quota');};assert.equal(await a.importLibrary([row]),false);a.storage.set=original;
 assert.equal(a.getLibrary().length,0);assert.match(a.get('#notesList').innerHTML,/Сохранённый текст/);assert.match(a.get('#list').innerHTML,/Игры/);
});
test('a concurrent note save cannot persist a library change that failed to save',async()=>{
 const a=await openVault();await a.importLibrary([row]);
 const write=a.storage.set;let calls=0;a.storage.set=function(key,value){if(++calls===1)throw Error('first write failed');return write.call(this,key,value);};
 const deletion=a.changeLibrary(items=>items.splice(0,1));a.get('#newNote').onclick();const note=a.submitNote('Одновременно','Заметка сохраняется');
 assert.equal(await deletion,false);await note;a.storage.set=write;
 const reopened=await openVault(a.storage);assert.equal(reopened.getLibrary().length,1);assert.match(reopened.get('#notesList').innerHTML,/Заметка сохраняется/);
});
