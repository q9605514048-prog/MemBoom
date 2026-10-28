import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';

test('remembered server session restores without a password request and rejects expired or unsafe records',async()=>{
 const storage=new Map();let calls=0;
 const context=vm.createContext({URL,Date,location:{protocol:'https:',origin:'https://example.github.io'},document:{querySelector(){return null}},localStorage:{getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},fetch(){calls++;throw Error('unexpected request')}});
 for(const path of ['auth-startup.js','auth-client.js','auth-ui.js'])vm.runInContext(await readFile(new URL('../scripts/'+path,import.meta.url),'utf8'),context);
 const app=context.MemBoomAuthUI.mount();
 const valid={endpoint:'https://api.example.com',token:'mbs_'+'a'.repeat(43),user:{id:'sample',login:'sample'},expiresAt:new Date(Date.now()+3600000).toISOString()};
 for(const invalid of [{...valid,expiresAt:'invalid'},{...valid,expiresAt:'2000-01-01T00:00:00Z'},{...valid,token:'wrong'},{...valid,endpoint:'http://api.example.com'}]){
  assert.equal(app.restore(invalid),false);assert.equal(app.session,null);
 }
 assert.equal(app.restore(valid),true);assert.equal(app.session.user.login,'sample');assert.equal(calls,0);
 assert.ok(!JSON.stringify([...storage]).includes(valid.token));
 app.clear();assert.equal(app.session,null);
});
