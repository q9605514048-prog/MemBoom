import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
test('public website can enter local mode without contacting an API',async()=>{
 const storage=new Map(),overlays=[];let networkCalls=0;
 function element(){return {style:{},children:[],events:{},append(child){this.children.push(child)},addEventListener(name,fn){this.events[name]=fn},querySelectorAll(){return []},querySelector(selector){if(selector==='form')return this.form??=element();if(selector==='button')return this.children.find(x=>x.type==='button')??{focus(){}};return null},remove(){this.removed=true},focus(){}};}
 const context=vm.createContext({location:{protocol:'https:',origin:'https://example.github.io'},localStorage:{getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},document:{querySelector(){return null},createElement:element,body:{append(el){overlays.push(el)}}},fetch(){networkCalls++;throw Error('unexpected API call')}});
 for(const path of ['auth-startup.js','auth-ui.js'])vm.runInContext(await readFile(new URL('../scripts/'+path,import.meta.url),'utf8'),context);
 const app=context.MemBoomAuthUI.mount();app.start();assert.equal(overlays.length,1);
 const button=overlays[0].form.children.find(x=>x.textContent==='Попробовать без сервера');assert.ok(button);button.events.click();
 assert.equal(overlays[0].removed,true);assert.equal(context.MemBoomAuthStartup.isLocal(),true);app.start();assert.equal(overlays.length,1);assert.equal(networkCalls,0);
});
