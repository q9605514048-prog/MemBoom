import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../scripts/auth-startup.js',import.meta.url),'utf8').catch(()=> '');
function fixture(storage=new Map()) {const c=vm.createContext({localStorage:{getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)}});vm.runInContext(source,c);return {state:c.MemBoomAuthStartup,storage};}
test('first visit offers a choice until successful authentication',()=>{const {state,storage}=fixture();assert.equal(state.read(),null);assert.equal(storage.size,0);});
test('a later visit remembers login and endpoint, never password or session token',()=>{const a=fixture();a.state.remember({user:{login:'qa_user'},endpoint:'https://example.test',token:'secret-token'},'secret-password');const b=fixture(a.storage);assert.equal(b.state.read().login,'qa_user');assert.equal(b.state.read().endpoint,'https://example.test');assert.ok(!JSON.stringify([...a.storage]).includes('secret-'));});
test('corrupt browser preferences fall back to first visit',()=>{const a=fixture();a.storage.set('memboom.auth.identity.v1','invalid');assert.equal(a.state.read(),null);});
test('local choice survives reload and server sign-in clears it',()=>{const a=fixture();a.state.useLocal();assert.equal(fixture(a.storage).state.isLocal(),true);a.state.remember({user:{login:'qa_user'},endpoint:'https://example.test'});assert.equal(a.state.isLocal(),false);});
