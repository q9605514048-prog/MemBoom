import {test} from 'node:test';import assert from 'node:assert/strict';import vm from 'node:vm';import {readFile} from 'node:fs/promises';
const model=await readFile(new URL('../scripts/library/model.js',import.meta.url),'utf8'),source=await readFile(new URL('../scripts/library/covers.js',import.meta.url),'utf8');
function setup(fetcher){
 let unlocked=true;const listeners={};const img={isConnected:true,hidden:true,loading:'lazy',dataset:{libraryCover:'id'},addEventListener:(name,fn)=>listeners[name]=fn,set src(value){this.value=value;if(this.loading==='eager')listeners.load?.();}};
 const c=vm.createContext({URL,AbortController,setTimeout:fn=>setImmediate(fn),location:{protocol:'http:'},fetch:fetcher});vm.runInContext(model,c);vm.runInContext(source,c);
 const client=c.MemBoomLibraryCovers.create(()=>unlocked),grid={querySelectorAll:()=>[img]},items=[{Id:'id',Url:'https://remanga.org/manga/example'}];
 return {client,grid,items,img,lock:()=>{unlocked=false;client.clear();}};
}
test('lazy poster container loads hidden image eagerly after becoming eligible',async()=>{
 const fixture=setup(async()=>({ok:true,json:async()=>({coverUrl:'https://remanga.org/media/titles/example/cover.webp'})}));
 fixture.client.show(fixture.grid,fixture.items);await new Promise(setImmediate);
 assert.equal(fixture.img.hidden,false);assert.equal(fixture.img.loading,'eager');
});

test('temporary metadata failure is retried and an imported small image is upgraded',async()=>{
 let calls=0;const fixture=setup(async()=>++calls<2?{ok:false,status:502}:{ok:true,json:async()=>({coverUrl:'https://remanga.org/media/titles/example/original.webp'})});
 fixture.items[0].CoverUrl='https://remanga.org/media/titles/example/small.webp';fixture.client.show(fixture.grid,fixture.items);
 for(let i=0;i<4;i++)await new Promise(setImmediate);
 assert.equal(calls,2);assert.equal(fixture.img.value,'https://remanga.org/media/titles/example/original.webp');assert.equal(fixture.img.hidden,false);
});
test('a cover response arriving after vault lock cannot repaint or retain its result',async()=>{
 let complete;const fixture=setup(()=>new Promise(resolve=>{complete=resolve;}));
 fixture.client.show(fixture.grid,fixture.items);fixture.lock();complete({ok:true,json:async()=>({coverUrl:'https://remanga.org/media/titles/example/cover.webp'})});await new Promise(setImmediate);
 assert.equal(fixture.img.value,undefined);assert.equal(fixture.img.hidden,true);
});
