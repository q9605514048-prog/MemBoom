import {test} from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {openVault} from './helpers/vault-harness.mjs';

async function key(password,salt){const material=await webcrypto.subtle.importKey('raw',new TextEncoder().encode(password),'PBKDF2',false,['deriveKey']);return webcrypto.subtle.deriveKey({name:'PBKDF2',salt,iterations:310000,hash:'SHA-256'},material,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);}
async function decrypt(envelope){return JSON.parse(new TextDecoder().decode(await webcrypto.subtle.decrypt({name:'AES-GCM',iv:Buffer.from(envelope.nonce,'base64')},await key('test!123',Buffer.from(envelope.salt,'base64')),Buffer.from(envelope.ciphertext,'base64'))));}
async function encrypt(value){const salt=webcrypto.getRandomValues(new Uint8Array(16)),nonce=webcrypto.getRandomValues(new Uint8Array(12));const ciphertext=await webcrypto.subtle.encrypt({name:'AES-GCM',iv:nonce},await key('test!123',salt),new TextEncoder().encode(JSON.stringify(value)));return {version:1,salt:Buffer.from(salt).toString('base64'),nonce:Buffer.from(nonce).toString('base64'),ciphertext:Buffer.from(ciphertext).toString('base64')};}
test('website uploads notes along with account categories',async()=>{
 let sent;const storage=new Map([['vault-sync-config',JSON.stringify({endpoint:'http://127.0.0.1:8788',token:'qa'})]]);
 const app=await openVault(storage,{fetch:async(_url,options)=>options.method==='PUT'?(sent=JSON.parse(options.body),{ok:true,status:200}):{ok:true,json:async()=>({revision:0,envelope:null})}});
 app.get('#addCategory').onclick();await app.submit('Работа');app.get('#newNote').onclick();await app.submitNote('Передать на телефон','Текст');
 await app.get('#syncBtn').onclick();assert.ok(sent);
 const plain=await decrypt(sent.envelope);assert.equal(plain.categories[0].name,'Работа');assert.equal(plain.notes[0].Title,'Передать на телефон');
});
test('downloaded phone notes persist on website and retain local notes',async()=>{
 const remote={categories:[{id:'remote',name:'Телефон',accounts:[]}],notes:[{Id:'remote-note',Title:'Заметка телефона',Content:'Текст',Pinned:false,UpdatedAt:'2026-10-28T00:00:00Z'}],library:[]};
 const envelope=await encrypt(remote);let sent;
 const storage=new Map([['vault-sync-config',JSON.stringify({endpoint:'http://127.0.0.1:8788',token:'qa'})]]);
 const app=await openVault(storage,{fetch:async(_url,options)=>options.method==='PUT'?(sent=JSON.parse(options.body),{ok:true,status:200}):{ok:true,json:async()=>({revision:3,envelope})}});
 app.get('#addCategory').onclick();await app.submit('Локальная категория');
 app.get('#newNote').onclick();await app.submitNote('Локальная заметка','Не потерять');await app.get('#syncBtn').onclick();
 const reopened=await openVault(storage);assert.match(reopened.get('#list').innerHTML,/Телефон/);assert.match(reopened.get('#notesList').innerHTML,/Заметка телефона/);assert.match(reopened.get('#notesList').innerHTML,/Локальная заметка/);
 assert.equal((await decrypt(sent.envelope)).notes.length,2);
 assert.match(reopened.get('#list').innerHTML,/Локальная категория/);
});
