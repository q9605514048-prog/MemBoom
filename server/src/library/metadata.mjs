// Public title metadata only. Credentials and user bookmark APIs are never used.
import {posterAliases} from './poster-aliases.mjs';
const cache=new Map(),pending=new Map();
export function titleUrl(value){
  const u=new URL(value),host=u.hostname.replace(/^www\./,'');
  if(u.protocol!=='https:'||u.username||u.password||u.port)throw Error('Invalid title URL');
  const manga=host==='remanga.org'&&/^\/manga\/[^/]+(?:\/(?:main|chapters))?\/?$/.test(u.pathname);
  const anime=host==='yummyanime.tv'&&/^\/\d+-[^/]+\.html$/.test(u.pathname);
  if(!manga&&!anime)throw Error('Unsupported title URL');
  u.hostname=host;u.search='';u.hash='';return {url:u,manga};
}
function coverUrl(value){
  const u=new URL(value);
  if(u.protocol!=='https:'||u.username||u.password||u.port||!((['remanga.org','api.remanga.org'].includes(u.hostname)&&u.pathname.startsWith('/media/'))||(u.hostname==='yummyanime.tv'&&/^\/(static\/posters|uploads)\//.test(u.pathname))))throw Error('Unsupported cover');
  if(u.hostname==='yummyanime.tv')u.pathname=u.pathname.replace(/^(\/static\/posters\/)\d+x\d+\//,'$1').replace('/uploads/posts/thumbs/','/uploads/posts/');
  return u.href;
}
async function request(endpoint,fetcher,options={}){
  let target=new URL(endpoint);const origin=target.origin,signal=AbortSignal.timeout(10000);
  for(let redirects=0;redirects<=3;redirects++){
    const response=await fetcher(target.href,{...options,redirect:'manual',signal});
    if(![301,302,303,307,308].includes(response.status))return {response,url:target};
    const location=response.headers.get('location');await response.body?.cancel();if(!location)throw Error('Invalid redirect');
    const next=new URL(location,target);
    if(next.origin!==origin||next.username||next.password||next.port)throw Error('Invalid redirect');
    if(origin==='https://yummyanime.tv')titleUrl(next.href);
    else if(!/^\/api\/(?:v2\/)?titles\/[^/]+\/$/.test(next.pathname))throw Error('Invalid redirect');
    target=next;
  }throw Error('Too many redirects');
}
async function read(response){
  if(!response.ok)throw Error('Metadata unavailable');
  const reader=response.body.getReader();let size=0,parts=[];
  try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>2_000_000)throw Error('Metadata too large');parts.push(value);}}
  finally{await reader.cancel().catch(()=>{});}
  return Buffer.concat(parts).toString('utf8');
}
export async function lookupMetadata(value,fetcher=fetch){
  const {url,manga}=titleUrl(value);
  const endpoint=manga?'https://api.remanga.org/api/titles/'+url.pathname.split('/')[2]+'/':url.href;
  const {response,url:resolvedUrl}=await request(endpoint,fetcher);
  if(!manga&&response.status===404){
    await response.body?.cancel();const slug=resolvedUrl.pathname.replace(/^\/\d+-/,'').replace(/\.html$/,'');
    const alias=posterAliases.get(resolvedUrl.pathname.match(/^\/(\d+)-/)[1]);
    const candidates=[...(alias?[alias]:[]),...['webp','jpg','jpeg'].map(extension=>slug+'.'+extension)];
    for(const filename of candidates){
      const candidate=coverUrl('https://yummyanime.tv/static/posters/'+filename);
      const {response:image}=await request(candidate,fetcher,{method:'HEAD'});
      if(image.ok&&/^image\/(webp|jpeg|png|avif)(?:;|$)/i.test(image.headers.get('content-type')??''))return {coverUrl:candidate};
    }throw Error('No cover');
  }
  const text=await read(response);
  if(manga){
    const data=JSON.parse(text),title=data.content??data;
    const path=title.cover?.high??title.img?.high??title.cover?.mid??title.img?.mid;if(typeof path!=='string')throw Error('No cover');
    const image=/^https:\/\//.test(path)?path:'https://remanga.org/media/'+path.replace(/^\/?media\//,'').replace(/^\//,'');
    return {title:String(title.main_name??'').slice(0,250),coverUrl:coverUrl(image)};
  }
  for(const tag of text.match(/<meta\b[^>]*>/gi)??[]){
    const attrs=Object.fromEntries([...tag.matchAll(/([\w:-]+)\s*=\s*(["'])(.*?)\2/gs)].map(x=>[x[1].toLowerCase(),x[3]]));
    if((attrs.property??attrs.name)==='og:image'&&attrs.content)return {coverUrl:coverUrl(attrs.content.replace(/&amp;/g,'&'))};
  }
  throw Error('No cover');
}
export async function cachedMetadata(value){
  const key=titleUrl(value).url.href,entry=cache.get(key);
  if(entry&&entry.expires>Date.now())return entry.value;
  if(pending.has(key))return pending.get(key);
  if(pending.size>=6)throw Error('Metadata busy');
  const job=lookupMetadata(key).then(value=>{
    cache.delete(key);cache.set(key,{value,expires:Date.now()+86400000});if(cache.size>1000)cache.delete(cache.keys().next().value);return value;
  }).finally(()=>pending.delete(key));pending.set(key,job);return job;
}
