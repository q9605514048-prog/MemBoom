import {test} from 'node:test';import assert from 'node:assert/strict';import vm from 'node:vm';import {readFile} from 'node:fs/promises';import {webcrypto} from 'node:crypto';
const c=vm.createContext({URL,crypto:webcrypto});vm.runInContext(await readFile(new URL('../scripts/library/model.js',import.meta.url),'utf8'),c);const model=c.MemBoomLibrary;
test('legacy collections remain in backups while recognized manga labels become statuses',()=>{
 const first=model.importRecords([{title:'Тайтл',url:'https://remanga.org/manga/example',status:'active',collections:['Хент'],coverUrl:'https://remanga.org/media/titles/example/cover.webp'}],'manga');
 const second=model.importRecords([{title:'Тайтл',url:first[0].Url,status:'planned',collections:['Лецуха','Хент']}],'manga');
 const merged=model.merge(first,second);assert.deepEqual(Array.from(merged.items[0].Collections),['Хент','Лецуха']);assert.equal(merged.items[0].Status,'hentai');assert.equal(merged.items[0].CoverUrl,first[0].CoverUrl);
 const restored=model.importRecords(JSON.parse(JSON.stringify(merged.items)),'manga');assert.deepEqual(Array.from(restored[0].Collections),['Хент','Лецуха']);assert.equal(model.filter(restored,'manga','','',false,'Лецуха').length,1);assert.equal(model.filter(restored,'manga','','',false,'Другая').length,0);
});

test('manga statuses accept user labels and migrate already saved entries without losing progress',()=>{
 for(const [name,status]of [['Хентай','hentai'],['Лецуха','yuri'],['много читать','long_read']]){
  const row=model.importRecords([{title:'Тайтл',url:'https://remanga.org/manga/x',status:name,progress:12.5}],'manga')[0];assert.equal(row.Status,status);
  const old={...row,Status:'active',StatusVersion:undefined,Collections:[name]};const migrated=model.migrate([old]);assert.equal(migrated[0].Status,status);assert.equal(migrated[0].Progress,12.5);assert.equal(old.Status,'active');
  const edited={...migrated[0],Status:'completed'};assert.equal(model.migrate([edited])[0].Status,'completed');
 }
});

test('new source statuses correct old imported reading status while preserving explicit edits',()=>{
 const imported=model.importRecords([{title:'Тайтл',url:'https://remanga.org/manga/x',status:'active',progress:12}],'manga');
 const incoming=model.importRecords([{title:'Тайтл',url:imported[0].Url,status:'Хентай'}],'manga');
 assert.equal(model.merge(imported,incoming).items[0].Status,'hentai');
 const manual=[{...imported[0],Status:'paused',StatusEdited:true}];assert.equal(model.merge(manual,incoming).items[0].Status,'paused');assert.equal(model.merge(imported,incoming).items[0].Progress,12);
 const ignored=model.importRecords([{title:'Тайтл',url:imported[0].Url,status:'Не интересно'}],'manga');assert.equal(model.merge(imported,ignored).items[0].Status,'not_interested');assert.equal(model.merge(manual,ignored).items[0].Status,'paused');
});

test('advancing a chapter keeps custom manga status while ordinary reading can complete',()=>{
 const row=model.importRecords([{title:'Тайтл',url:'https://remanga.org/manga/x',status:'Много читать',progress:1,total:2}],'manga')[0];
 assert.equal(model.advance(row).Status,'long_read');assert.equal(model.advance(row).Progress,2);assert.equal(model.advance({...row,Status:'active'}).Status,'completed');assert.throws(()=>model.advance({...row,Progress:2}));
});
test('old category notes migrate and not interested remains a distinct manga status',()=>{
 const row=model.importRecords([{title:'Тайтл',url:'https://remanga.org/manga/example',status:'Не интересно',notes:'Категория ReManga: Моя папка'}],'manga')[0];
 assert.equal(row.Status,'not_interested');assert.deepEqual(Array.from(row.Collections),['Моя папка']);
});
test('unsafe imported cover URLs and invalid folder lists are rejected',()=>{
 for(const coverUrl of ['javascript:alert(1)','https://evil.test/cover.jpg','https://remanga.org.evil.test/media/a.jpg'])assert.throws(()=>model.importRecords([{title:'Тайтл',url:'https://remanga.org/manga/x',coverUrl}],'manga'));
 assert.throws(()=>model.importRecords([{title:'Тайтл',url:'https://remanga.org/manga/x',collections:['x'.repeat(101)]}],'manga'));
});
