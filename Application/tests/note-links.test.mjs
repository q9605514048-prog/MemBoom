import {test} from 'node:test';import assert from 'node:assert/strict';import vm from 'node:vm';import {readFile} from 'node:fs/promises';import {webcrypto} from 'node:crypto';
const c=vm.createContext({URL,crypto:webcrypto});vm.runInContext(await readFile(new URL('../scripts/notes-model.js',import.meta.url),'utf8'),c);const model=c.MemBoomNotes;
const base={Id:'note',Title:'Документы',Content:'Текст',Pinned:false,UpdatedAt:'2026-10-27T00:00:00Z'};
test('cloud file links survive backup and can be found by name',()=>{
 const note={...base,Links:[{Name:'Договор',Url:'https://drive.google.com/file/d/example/view?resourcekey=test'},{Name:'Чертёж',Url:'https://disk.yandex.ru/d/example'}]};
 const restored=model.parse(JSON.parse(JSON.stringify({categories:[],notes:[note]}))).notes[0];assert.equal(restored.Links[0].Url,note.Links[0].Url);assert.equal(model.search([restored],'чертёж').length,1);
 assert.equal(model.link(note.Links[0]).Provider,'Google Диск');assert.equal(model.link(note.Links[1]).Provider,'Яндекс Диск');
});
test('unsafe links and duplicate file URLs are rejected before saving',()=>{
 for(const Url of ['javascript:alert(1)','http://drive.google.com/file/x','https://drive.google.com.evil.test/x','https://user:pass@disk.yandex.ru/d/x','https://disk.yandex.ru:8443/d/x','https://example.com/file'])assert.throws(()=>model.validate([{...base,Links:[{Name:'Файл',Url}]}]));
 const link={Name:'Файл',Url:'https://yadi.sk/d/example'};assert.throws(()=>model.validate([{...base,Links:[link,link]}]));assert.throws(()=>model.validate([{...base,Links:Array.from({length:21},(_,i)=>({...link,Url:link.Url+i}))}]));
});
test('import does not discard a note with different file links and remains idempotent',()=>{
 const first={...base,Links:[{Name:'Первый',Url:'https://drive.google.com/file/d/first/view'}]},second={...base,Links:[{Name:'Второй',Url:'https://docs.google.com/document/d/second/edit'}]};
 const merged=model.merge([first],[second]);assert.equal(merged.length,2);assert.equal(model.merge(merged,[second]).length,2);assert.equal(model.parse({categories:[],notes:[base]}).notes.length,1);
});
