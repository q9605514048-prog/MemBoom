import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {webcrypto} from 'node:crypto';
import vm from 'node:vm';
const ctx=vm.createContext({URL,crypto:webcrypto,AbortController,setTimeout,clearTimeout,Date});
for(const file of ['library/model.js','library/anime-follow.js'])vm.runInContext(await readFile(new URL('../scripts/'+file,import.meta.url),'utf8'),ctx);
const api=ctx.MemBoomAnimeFollow;
test('source status and favorite update while local notes, progress and manga survive',()=>{
 const local=ctx.MemBoomLibrary.importRecords([{Title:'Old name',Url:'https://yummyanime.tv/7-old.html',Status:'planned',Progress:5,Total:12,Notes:'Local private note',Favorite:true}],'anime');
 const incoming=[{Title:'New name',Url:'https://yummyanime.tv/7-new.html',Status:'active',Progress:null,Total:null,Notes:'',Favorite:false}];
 const result=api.mergeProfile(local,{complete:true,items:incoming});
 assert.equal(result.updated,1);assert.equal(result.added,0);assert.equal(result.items[0].Progress,5);assert.equal(result.items[0].Notes,'Local private note');assert.equal(result.items[0].Status,'active');assert.equal(result.items[0].Favorite,false);assert.equal(result.items[0].Id,local[0].Id);
 assert.equal(api.mergeProfile(result.items,{complete:true,items:incoming}).updated,0);assert.equal(local[0].Status,'planned');
 assert.throws(()=>api.mergeProfile(local,{complete:false,items:[]}));
});
test('a delayed source response cannot modify the vault after locking',async()=>{
 let release,applied=0;const result=new Promise(resolve=>release=resolve);
 const loop=api.createFollower({fetchProfile:()=>result,apply:()=>{applied++},isUnlocked:()=>true,isVisible:()=>true,isOnline:()=>true,status(){},schedule(){return 1},cancel(){}});
 loop.start('profile');loop.stop();release({complete:true,items:[]});await result;await new Promise(resolve=>setTimeout(resolve,0));assert.equal(applied,0);
});
test('hidden or offline browser does not poll and uses a one minute interval',async()=>{
 let calls=0,delay=0;const loop=api.createFollower({fetchProfile:async()=>{calls++},apply(){},isUnlocked:()=>true,isVisible:()=>false,isOnline:()=>true,status(){},schedule(_fn,ms){delay=ms;return 1},cancel(){}});
 loop.start('profile');await new Promise(resolve=>setTimeout(resolve,0));assert.equal(calls,0);assert.equal(delay,60000);loop.stop();
});
