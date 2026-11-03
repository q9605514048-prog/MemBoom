import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from '../src/server.mjs';
import {publicProfiles} from '../src/library/yummy-profile.mjs';
test('public profile route rejects unsafe URLs, foreign origins and all write methods',async t=>{
 const server=createServer({token:'synthetic-testing-token-123',staticDirectory:'.'});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));const base='http://127.0.0.1:'+server.address().port;
 assert.equal((await fetch(base+'/api/library/anime-profile?url='+encodeURIComponent('https://127.0.0.1/user/id123'))).status,400);
 assert.equal((await fetch(base+'/api/library/anime-source',{method:'POST'})).status,405);
 assert.equal((await fetch(base+'/api/library/anime-source',{headers:{origin:'https://unrelated.example'}})).status,403);
 const before=publicProfiles.get;publicProfiles.get=async()=>({complete:true,items:[],checkedAt:'2026-11-03T00:00:00Z'});t.after(()=>publicProfiles.get=before);
 const result=await fetch(base+'/api/library/anime-profile?url='+encodeURIComponent('https://yummyanime.tv/user/id123'));assert.equal(result.status,200);assert.equal((await result.json()).complete,true);
 publicProfiles.get=async()=>{throw Error('synthetic unavailable');};assert.equal((await fetch(base+'/api/library/anime-profile?url='+encodeURIComponent('https://yummyanime.tv/user/id123'))).status,502);
});
