import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const ctx=vm.createContext({});
vm.runInContext(await readFile(new URL('../scripts/ui/vault-presentation.js',import.meta.url),'utf8'),ctx);
const ui=ctx.MemBoomPresentation;
test('overview never returns field values or note contents',()=>{
 const result=ui.overview([{id:'c',name:'Work',accounts:[{id:'a',name:'Example',favorite:true,fields:[{label:'Password',value:'NEVER_RENDER_THIS'}]}]}],[{Id:'n',Title:'Pinned',Content:'PRIVATE_NOTE',Pinned:true,UpdatedAt:'2026-01-01T00:00:00Z'}],[]);
 assert.equal(result.accounts,1);assert.equal(result.favorites,1);assert.equal(result.notes,1);
 assert.ok(!JSON.stringify(result).includes('NEVER_RENDER_THIS'));assert.ok(!JSON.stringify(result).includes('PRIVATE_NOTE'));
});
test('unknown and sensitive fields are secret by default in both languages',()=>{
 for(const name of ['Пароль','Password','API token','Код восстановления','PIN','Моё поле'])assert.equal(ui.isSecret(name),true,name);
 for(const name of ['Логин','Почта','Email','Username','URL'])assert.equal(ui.isSecret(name),false,name);
});
test('note sorting is stable and does not mutate vault order',()=>{
 const notes=[{Id:'a',Title:'Zulu',Pinned:false,UpdatedAt:'2026-01-01'},{Id:'b',Title:'Alpha',Pinned:true,UpdatedAt:'2026-02-01'}];
 assert.equal(ui.sortNotes(notes,'title')[0].Id,'b');assert.equal(notes[0].Id,'a');
 assert.equal(ui.sortNotes(notes,'oldest')[0].Id,'a');
});
