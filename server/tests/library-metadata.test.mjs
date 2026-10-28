import {test} from 'node:test';
import assert from 'node:assert/strict';
import {lookupMetadata} from '../src/library/metadata.mjs';

test('ReManga public metadata yields a cover without credentials',async()=>{
 const calls=[];const data=await lookupMetadata('https://remanga.org/manga/example/main',async(url,options)=>{
  calls.push({url,options});return new Response(JSON.stringify({content:{main_name:'Манга',cover:{mid:'titles/example/small.webp',high:'titles/example/original.webp'}}}));
 });
 assert.equal(data.coverUrl,'https://remanga.org/media/titles/example/original.webp');assert.equal(data.title,'Манга');
 assert.equal(calls[0].url,'https://api.remanga.org/api/titles/example/');assert.equal(calls[0].options.redirect,'manual');assert.equal(calls[0].options.headers,undefined);
});

test('YummyAnime follows a renamed title on its own domain and upgrades thumbnail URLs',async()=>{
 let calls=0;const data=await lookupMetadata('https://yummyanime.tv/123-old.html',async()=>++calls===1?new Response('',{status:301,headers:{location:'/123-new.html'}}):new Response('<meta property="og:image" content="https://yummyanime.tv/static/posters/200x300/example.webp">'));
 assert.equal(calls,2);assert.equal(data.coverUrl,'https://yummyanime.tv/static/posters/example.webp');
 await assert.rejects(lookupMetadata('https://yummyanime.tv/123-old.html',async()=>new Response('',{status:302,headers:{location:'http://127.0.0.1/private'}})));
});
test('a missing anime page can use its existing original poster, never a fabricated success',async()=>{
 const calls=[];const data=await lookupMetadata('https://yummyanime.tv/179-example.html',async(url,options)=>{calls.push({url,method:options.method});return options.method==='HEAD'?new Response(null,{headers:{'content-type':'image/webp'}}):new Response('',{status:404});});
 assert.equal(data.coverUrl,'https://yummyanime.tv/static/posters/example.webp');assert.equal(calls[1].method,'HEAD');
 await assert.rejects(lookupMetadata('https://yummyanime.tv/179-example.html',async()=>new Response('',{status:404})));
});

test('fallback after a rename uses the final canonical slug',async()=>{
 const data=await lookupMetadata('https://yummyanime.tv/12345-old.html',async(url,options)=>{
  if(url.endsWith('12345-old.html'))return new Response('',{status:301,headers:{location:'/12345-current.html'}});
  if(options.method==='HEAD'){assert.equal(url,'https://yummyanime.tv/static/posters/current.webp');return new Response(null,{headers:{'content-type':'image/webp'}});}
  return new Response('',{status:404});
 });assert.equal(data.coverUrl,'https://yummyanime.tv/static/posters/current.webp');
});
test('YummyAnime poster is read from either order of Open Graph attributes',async()=>{
 const data=await lookupMetadata('https://yummyanime.tv/123-example.html',async()=>new Response('<meta content="https://yummyanime.tv/static/posters/test.webp" property="og:image">'));
 assert.equal(data.coverUrl,'https://yummyanime.tv/static/posters/test.webp');
});
test('metadata rejects arbitrary hosts, credentials, redirects and foreign image URLs',async()=>{
 for(const value of ['https://127.0.0.1/manga/x','http://remanga.org/manga/x','https://u:p@remanga.org/manga/x','https://remanga.org:8443/manga/x','https://remanga.org/user/1'])await assert.rejects(lookupMetadata(value,()=>{throw Error('must not fetch');}));
 await assert.rejects(lookupMetadata('https://yummyanime.tv/123-x.html',async()=>new Response('<meta property="og:image" content="https://evil.test/tracker.jpg">')));
 await assert.rejects(lookupMetadata('https://remanga.org/manga/x',async()=>new Response('',{status:404})));
});
