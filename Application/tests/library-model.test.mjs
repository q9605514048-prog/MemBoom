import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {webcrypto} from 'node:crypto';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../scripts/library/model.js',import.meta.url),'utf8').catch(()=>'');
const context=vm.createContext({URL,crypto:webcrypto});vm.runInContext(source,context);const model=context.MemBoomLibrary;
test('YummyAnime imports preserve status and favorites without inventing episode progress',()=>{
 const entries=model.importRecords([{title:'Тайтл',url:'https://yummyanime.tv/123-title.html',status:'Просмотрено'},{title:'Тайтл',url:'https://yummyanime.tv/123-title.html',status:'Любимое'}],'anime');
 assert.equal(entries.length,1);assert.equal(entries[0].Status,'completed');assert.equal(entries[0].Favorite,true);assert.equal(entries[0].Progress,null);
});
test('repeated import skips existing URLs and preserves manually updated progress',()=>{
 const imported=model.importRecords([{title:'Манга',url:'https://remanga.org/manga/test/main',status:'Читаю'}],'manga');
 const local=[{...imported[0],Progress:12.5}];const result=model.merge(local,imported);
 assert.equal(result.added,0);assert.equal(result.items.length,1);assert.equal(result.items[0].Progress,12.5);
});

test('corrected ReManga export repairs placeholder names and enriches missing metadata only',()=>{
 const original=model.importRecords([{title:'title image',url:'https://remanga.org/manga/fix',status:'other'}],'manga');
 const corrected=model.importRecords([{title:'Верное название',url:'https://remanga.org/manga/fix',status:'completed',favorite:true,notes:'Категория ReManga: Прочитано'}],'manga');
 const result=model.merge([{...original[0],Progress:12.5}],corrected);
 assert.equal(result.updated,1);assert.equal(result.added,0);assert.equal(result.items[0].Title,'Верное название');assert.equal(result.items[0].Status,'completed');assert.equal(result.items[0].Progress,12.5);assert.equal(result.items[0].Favorite,true);
 const manual=model.merge([{...original[0],Title:'Моё название',Status:'paused',Notes:'Моя заметка'}],corrected);
 assert.equal(manual.items[0].Title,'Моё название');assert.equal(manual.items[0].Status,'paused');assert.equal(manual.items[0].Notes,'Моя заметка');
 assert.equal(model.merge(result.items,corrected).updated,0);
});
test('unsafe links, invalid progress and duplicate ids are rejected before changing storage',()=>{
 for(const url of ['javascript:alert(1)','https://remanga.org.evil.test/manga/test','https://u:p@remanga.org/manga/test'])assert.throws(()=>model.importRecords([{title:'a',url}],'manga'));
 const row=model.importRecords([{title:'a',url:'https://yummyanime.tv/1-a.html'}],'anime')[0];
 assert.throws(()=>model.validate([{...row,Progress:-1}]));assert.throws(()=>model.validate([row,row]));
});
test('CSV export data and unknown categories can be imported with a preview',()=>{
 const rows=model.parseFile('title,url,status,progress\n"Название, часть 2",https://yummyanime.tv/12-test.html,Запланировано,\n','anime');
 assert.equal(rows[0].Title,'Название, часть 2');assert.equal(rows[0].Status,'planned');assert.equal(rows[0].Progress,null);
});
