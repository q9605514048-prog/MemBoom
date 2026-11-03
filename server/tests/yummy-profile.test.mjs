import {test} from 'node:test';
import assert from 'node:assert/strict';
import {profileUrl,parseCards,lookupProfile,createProfileCache} from '../src/library/yummy-profile.mjs';
const card=(id,title)=>`<div data-fav="post" data-post-id="${id}" data-list-id="1"><a class="popular-item__img" href="/${id}-demo.html"><img src="/static/posters/50x70/demo.webp"></a><a class="popular-item__title" href="/${id}-demo.html">${title}</a></div>`;
test('only public numeric YummyAnime profiles can be requested',()=>{
 assert.equal(profileUrl('https://yummyanime.tv/user/id123').id,'123');
 for(const x of ['http://yummyanime.tv/user/id123','https://evil.test/user/id123','https://yummyanime.tv@127.0.0.1/user/id123','https://yummyanime.tv:8443/user/id123','https://yummyanime.tv/user/id123?x=1'])assert.throws(()=>profileUrl(x));
});
test('cards yield titles, canonical links and full-size posters without HTML',()=>{
 const rows=parseCards(card('7','Magic &amp; memory &#9733;'),'active');
 assert.equal(rows[0].Title,'Magic & memory ★');assert.equal(rows[0].CoverUrl,'https://yummyanime.tv/static/posters/demo.webp');assert.equal(rows[0].Progress,null);
 assert.throws(()=>parseCards(card('7','Demo').replaceAll('/7-demo.html','https://evil.test/7-demo.html'),'active'));
});
test('all six lists are loaded including favorites and incomplete responses never become a success',async()=>{
 const page=Array.from({length:6},(_,i)=>`<span data-fav="item-count" data-list-id="${i+1}">${i===0||i===5?1:0}</span>`).join('');let calls=0;
 const fetcher=async(url,opts)=>{calls++;if(!opts?.body)return new Response(page);const data=opts.body;assert.equal(data.get('action'),'load');assert.equal(data.get('limit'),'0');return new Response(JSON.stringify({success:true,response:['1','6'].includes(data.get('list_id'))?card('7','Demo'):''}));};
 const result=await lookupProfile('https://yummyanime.tv/user/id123',fetcher);assert.equal(result.items.length,1);assert.equal(result.items[0].Favorite,true);assert.equal(result.items[0].Status,'active');assert.equal(calls,7);
 await assert.rejects(lookupProfile('https://yummyanime.tv/user/id123',async(_url,opts)=>new Response(opts?.body?JSON.stringify({success:true,response:''}):page)));
});
test('profile cache coalesces callers and enforces a fresh source read after the TTL',async()=>{
 let now=0,calls=0;const cache=createProfileCache({now:()=>now,lookup:async()=>{calls++;return {items:[],checkedAt:'2026-11-03T00:00:00Z'}}});
 await Promise.all([cache.get('https://yummyanime.tv/user/id123'),cache.get('https://yummyanime.tv/user/id123')]);assert.equal(calls,1);
 now=61000;await cache.get('https://yummyanime.tv/user/id123');assert.equal(calls,2);
});
