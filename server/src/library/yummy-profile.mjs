/* Read-only public list adapter. No cookies, user passwords or arbitrary hosts. */
import {coverUrl} from './metadata.mjs';
const ORIGIN='https://yummyanime.tv',states=['active','completed','planned','paused','dropped','other'];
export function profileUrl(value){
 const url=new URL(value);if(url.origin!==ORIGIN||url.username||url.password||url.port||url.search||url.hash||!/^\/user\/id[1-9]\d{0,11}\/?$/.test(url.pathname))throw Error('invalid_profile_url');
 return {id:url.pathname.match(/id(\d+)/)[1],url:ORIGIN+url.pathname.replace(/\/$/,'')};
}
const attributes=tag=>Object.fromEntries([...tag.matchAll(/([\w:-]+)\s*=\s*(["'])(.*?)\2/gs)].map(x=>[x[1].toLowerCase(),x[3]]));
function text(value){return value.replace(/<[^>]*>/g,'').replace(/&#(x[0-9a-f]+|\d+);/gi,(_,n)=>{const p=n[0].toLowerCase()==='x'?parseInt(n.slice(1),16):Number(n);return p>0&&p<=0x10ffff?String.fromCodePoint(p):'';}).replace(/&(amp|quot|apos|lt|gt|nbsp);/g,(_,n)=>({amp:'&',quot:'"',apos:"'",lt:'<',gt:'>',nbsp:' '}[n])).replace(/\s+/g,' ').trim();}
function titleLink(value){const url=new URL(value,ORIGIN);if(url.origin!==ORIGIN||url.username||url.password||url.search||url.hash||!/^\/\d+-[^/]+\.html$/.test(url.pathname))throw Error('invalid_title_url');return url.href;}
export function parseCards(html,status){
 const starts=[...html.matchAll(/<div\b(?=[^>]*\bdata-fav=["']post["'])[^>]*>/gi)];
 return starts.map((match,i)=>{
  const segment=html.slice(match.index,starts[i+1]?.index??html.length),tags=[...segment.matchAll(/<a\b([^>]*)>([\s\S]*?)<\/a>/gi)];
  const title=tags.find(x=>(attributes(x[1]).class??'').split(/\s+/).includes('popular-item__title'));if(!title)throw Error('missing_title');
  const Title=text(title[2]);if(!Title||Title.length>250)throw Error('invalid_title');
  const image=segment.match(/<img\b[^>]*>/i),path=image?attributes(image[0]).src:'';
  const CoverUrl=path?coverUrl(new URL(path,ORIGIN).href.replace(/\/posters\/\d+x\d+\//,'/posters/')):'';
  return {Kind:'anime',Title,Url:titleLink(attributes(title[1]).href),Status:status,Favorite:false,Progress:null,Total:null,Notes:'',CoverUrl};
 });
}
async function readBounded(response){
 if(!response.ok)throw Error('source_unavailable');let bytes=0;const chunks=[];
 for await(const chunk of response.body){bytes+=chunk.length;if(bytes>2_000_000)throw Error('source_too_large');chunks.push(chunk);}
 return Buffer.concat(chunks).toString('utf8');
}
export async function lookupProfile(value,fetcher=fetch){
 const profile=profileUrl(value),signal=AbortSignal.timeout(25000);
 const request=(url,options={})=>fetcher(url,{...options,redirect:'error',signal,credentials:'omit',headers:{accept:'text/html, application/json','user-agent':'MemBoom/0.10 public profile reader',...options.headers}});
 const page=await readBounded(await request(profile.url));
 const counts=new Map([...page.matchAll(/<span\b([^>]*)>(\d+)<\/span>/gi)].map(x=>[attributes(x[1]),Number(x[2])]).filter(([a])=>a['data-fav']==='item-count').map(([a,n])=>[Number(a['data-list-id']),n]));
 if(![1,2,3,4,5,6].every(n=>counts.has(n))||[...counts.values()].some(n=>n>5000))throw Error('profile_private_or_changed');
 const all=new Map();let favoriteRows=[];
 for(let list=1;list<=6;list++){
  const body=new URLSearchParams({mod:'fav',action:'load',user_id:profile.id,list_id:String(list),limit:'0',filter:'null',order:'[]'});
  const json=JSON.parse(await readBounded(await request(ORIGIN+'/engine/ajax/controller.php',{method:'POST',body})));
  if(json.success!==true||typeof json.response!=='string')throw Error('source_unavailable');
  const rows=parseCards(json.response,states[list-1]),unique=new Set(rows.map(x=>x.Url));
  if(unique.size!==counts.get(list))throw Error('incomplete_profile');
  if(list===6){favoriteRows=rows;continue;}
  for(const row of rows){if(all.has(row.Url))throw Error('inconsistent_profile');all.set(row.Url,row);}
 }
 for(const row of favoriteRows){if(!all.has(row.Url))all.set(row.Url,row);all.get(row.Url).Favorite=true;}
 return {profile:profile.url,items:[...all.values()],checkedAt:new Date().toISOString(),intervalSeconds:60,complete:true};
}
export function createProfileCache({lookup=lookupProfile,now=Date.now}={}){
 const cache=new Map(),pending=new Map();
 async function get(value){const key=profileUrl(value).url,entry=cache.get(key);if(entry&&now()-entry.time<55000)return entry.data;if(pending.has(key))return pending.get(key);if(pending.size>=4)throw Error('source_busy');
  const job=lookup(key).then(data=>{cache.set(key,{time:now(),data});if(cache.size>30)cache.delete(cache.keys().next().value);return data;}).finally(()=>pending.delete(key));pending.set(key,job);return job;}
 return {get};
}
export const publicProfiles=createProfileCache();
