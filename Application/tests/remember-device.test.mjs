import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {webcrypto} from 'node:crypto';
import vm from 'node:vm';
const source=await readFile(new URL('../scripts/remember-device.js',import.meta.url),'utf8');
function fixture(storage){const c=vm.createContext({crypto:webcrypto,TextEncoder,TextDecoder,Uint8Array});vm.runInContext(source,c);return c.MemBoomRemember.create({storage,crypto:webcrypto});}
test('browser restart restores encrypted unlock and session without storing plain credentials',async()=>{
 let stored;const storage={read:async()=>stored,write:async value=>{stored=structuredClone(value)},remove:async()=>{stored=null}};
 const first=fixture(storage);await first.save('private-master!123',{token:'private-session-token'});
 assert.equal(stored.key.extractable,false);assert.ok(!JSON.stringify(stored).includes('private-'));
 await assert.rejects(webcrypto.subtle.exportKey('raw',stored.key));
 const restored=await fixture(storage).load();assert.equal(restored.password,'private-master!123');assert.equal(restored.session.token,'private-session-token');
 await first.clear();assert.equal(await fixture(storage).load(),null);
});
test('manual lock cancels a save that is still preparing its encryption key',async()=>{
 let stored;const storage={read:async()=>stored,write:async value=>{stored=value},remove:async()=>{stored=null}};
 const remember=fixture(storage),pending=remember.save('private-master!123');await remember.clear();await pending;
 assert.equal(await remember.load(),null);
});
test('corrupt device data is rejected and storage failures do not expose the password',async()=>{
 let stored;const storage={read:async()=>stored,write:async value=>{stored=value},remove:async()=>{stored=null}};
 const remember=fixture(storage);await remember.save('private-master!123');stored.ciphertext=new Uint8Array(32).buffer;
 await assert.rejects(remember.load());
 const unavailable=fixture({read:async()=>{throw Error('unavailable')},write:async()=>{throw Error('unavailable')},remove:async()=>{}});
 await assert.rejects(unavailable.save('private-master!123'));
});
