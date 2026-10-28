import {test} from 'node:test';import assert from 'node:assert/strict';import vm from 'node:vm';import {readFile} from 'node:fs/promises';import {webcrypto} from 'node:crypto';
const source=await readFile(new URL('../scripts/library/remanga-export.js',import.meta.url),'utf8').catch(()=>'');
const c=vm.createContext({URL,crypto:webcrypto});vm.runInContext(source,c);const exporter=c.MemBoomRemangaExporter;
const modelSource=await readFile(new URL('../scripts/library/model.js',import.meta.url),'utf8');vm.runInContext(modelSource,c);
test('exporter accepts title links, deduplicates cover/title links and ignores navigation',()=>{
 const rows=exporter.collect([{href:'https://remanga.org/manga/example/main',title:'Манга'},{href:'https://remanga.org/manga/example/chapters',title:'Манга'},{href:'https://remanga.org/manga/top',title:'Топы'},{href:'https://evil.test/manga/x',title:'Чужая ссылка'}],'active');
 assert.equal(rows.length,1);assert.equal(rows[0].title,'Манга');assert.equal(rows[0].status,'active');assert.equal(rows[0].progress,null);
});
test('several batches preserve favorites and known status without duplicate titles',()=>{
 const first=exporter.collect([{href:'https://remanga.org/manga/example',title:'Манга'}],'favorite');
 const next=exporter.collect([{href:'https://remanga.org/manga/example',title:'Манга'}],'completed');
 const all=exporter.merge(first,next);assert.equal(all.length,1);assert.equal(all[0].favorite,true);assert.equal(all[0].status,'completed');
 const imported=c.MemBoomLibrary.parseFile(JSON.stringify({entries:all}),'manga');assert.equal(imported[0].Favorite,true);assert.equal(imported[0].Status,'completed');
});
test('bookmarklet contains the exporter and does not load remote code or read credentials',()=>{
 const code=decodeURIComponent(exporter.bookmarklet().slice('javascript:'.length));assert.ok(code.includes('createRemangaExporter'));assert.doesNotMatch(code,/document\.cookie|localStorage|sessionStorage|Authorization|https?:\/\/[^\s]+\.js/);
});

test('real card structure uses visible title instead of generic image alt',()=>{
 const card={href:'https://remanga.org/manga/example/main',textContent:'Манхва 2026 15 / 60 Правильное название',getClientRects:()=>[{}],closest:()=>null,getAttribute:()=>null,
 querySelector:selector=>selector.includes('line-clamp')?{textContent:'Правильное название'}:selector==='img'?{getAttribute:()=> 'title image'}:null};
 const doc={querySelectorAll:()=>[card]};
 assert.equal(exporter.readPage('completed',doc)[0].title,'Правильное название');
 assert.equal(exporter.collect([{href:card.href,title:'title image'}]).length,0);
});

test('ReManga types differ from YummyAnime and folder IDs are not status IDs',()=>{
 exporter.capture('https://api.remanga.org/api/v2/users/12345/user_bookmarks/',{results:[{id:721,type:2,name:'Буду читать'},{id:722,type:3,name:'Прочитано'},{id:723,type:7,name:'Любимое'},{id:724,type:8,name:'Моя подборка'}]});
 const rows=exporter.fromResponse({results:[{title:{dir:'first',main_name:'Первая'},type:721},{title:{dir:'second',main_name:'Вторая'},type:722},{title:{dir:'third',main_name:'Третья'},type:723},{title:{dir:'fourth',main_name:'Четвёртая',status:{id:3,name:'Завершён'}},type:724}]});
 assert.deepEqual(Array.from(rows,x=>x.status),['planned','completed','other','other']);assert.equal(rows[2].favorite,true);
 assert.equal(rows[3].notes,'Категория ReManga: Моя подборка');
 assert.equal(exporter.capture('https://evil.test/api/v2/users/12345/bookmarks/',{results:[]}),false);
});

test('captures multiple API pages beyond 30, retains names and statuses in MemBoom import',()=>{
 const tool=c.createRemangaExporter();
 tool.capture('https://api.remanga.org/api/v2/users/12345/user_bookmarks/',{results:[{id:900,type:1,name:'Читаю'}]});
 for(let page=1;page<=3;page++)tool.capture(`https://api.remanga.org/api/v2/users/12345/bookmarks/?type=900&page=${page}`,{count:65,next:page<3?'next':null,results:Array.from({length:page===3?5:30},(_,i)=>({title:{dir:`title-${(page-1)*30+i}`,main_name:`Манга ${(page-1)*30+i}`},type:900}))});
 const rows=tool.snapshot();assert.equal(rows.length,65);assert.ok(rows.every(x=>x.title.startsWith('Манга ')&&x.status==='active'));
 assert.equal(c.MemBoomLibrary.parseFile(JSON.stringify({entries:rows}),'manga').length,65);
});

test('manual fallback covers API-only titles without folder metadata and remains scoped to category',()=>{
 const tool=c.createRemangaExporter();
 tool.capture('https://api.remanga.org/api/v2/users/12345/bookmarks/?type=120&page=2',{results:[{title:{dir:'api-only',main_name:'Ещё не показано'},type:120}]},'completed');
 tool.capture('https://api.remanga.org/api/v2/users/12345/bookmarks/?type=121&page=1',{results:[{title:{dir:'another',main_name:'Другая категория'},type:121}]},'planned');
 assert.equal(tool.snapshot().find(x=>x.url.endsWith('/api-only')).status,'completed');
 assert.equal(tool.snapshot().find(x=>x.url.endsWith('/another')).status,'planned');
});

test('named ReManga categories export as the requested manga statuses',()=>{
 const tool=c.createRemangaExporter();tool.capture('https://api.remanga.org/api/v2/users/12345/user_bookmarks/',{results:[{id:1,type:8,name:'Хентай'},{id:2,type:8,name:'Лецуха'},{id:3,type:8,name:'Много читать'}]});
 const rows=tool.fromResponse({results:[1,2,3].map(id=>({type:id,title:{dir:'status-'+id,main_name:'Тайтл '+id}}))});assert.deepEqual(Array.from(rows,x=>x.status),['hentai','yuri','long_read']);
});

test('requested category aliases retain their status through export and website import',()=>{
 const names=['Не интересно','неинтересно','хент','хент ( хентай )','много глав','много читать ( много глав )','лецуха'];
 const expected=['not_interested','not_interested','hentai','hentai','long_read','long_read','yuri'];
 const tool=c.createRemangaExporter();tool.capture('https://api.remanga.org/api/v2/users/12345/user_bookmarks/',{results:names.map((name,i)=>({id:100+i,type:8,name}))});
 const rows=tool.fromResponse({results:names.map((_,i)=>({type:100+i,title:{dir:'alias-'+i,main_name:'Тайтл '+i}}))});
 assert.deepEqual(Array.from(rows,x=>x.status),expected);
 assert.deepEqual(Array.from(c.MemBoomLibrary.parseFile(JSON.stringify({entries:rows}),'manga'),x=>x.Status),expected);
});

test('browser observer preserves fetch result and scrolls through virtualized batches',()=>{
 let y=0,batch=0,requests=0;const queue=[],nodes=new Map();
 const element=()=>({textContent:'',value:'',disabled:false,append(){},remove(){}});
 const panel={style:{},querySelector:s=>{if(!nodes.has(s))nodes.set(s,element());return nodes.get(s);},remove(){}};
 const document={createElement:tag=>tag==='section'?panel:element(),body:{append(){}},querySelector:()=>null,scrollingElement:{scrollHeight:2000},querySelectorAll:()=>Array.from({length:30},(_,i)=>({href:`https://remanga.org/manga/virtual-${batch*30+i}`,textContent:'',getClientRects:()=>[{}],closest:()=>null,getAttribute:()=>null,querySelector:s=>s.includes('line-clamp')?{textContent:`Манга ${batch*30+i}`}:null}))};
 const response={ok:true,clone:()=>({json:async()=>({results:[]})})};const originalPromise=Promise.resolve(response);
 const window={innerHeight:1000,get scrollY(){return y;},scrollTo(_x,next){y=next;},scrollBy(_x,n){y=Math.min(y+n,1000);if(y===1000&&batch<2){batch++;y=0;}},fetch(){requests++;return originalPromise;}};
 const ctx=vm.createContext({URL,document,window,location:{href:'https://remanga.org/user/bookmarks?type=900',protocol:'https:',hostname:'remanga.org',pathname:'/user/bookmarks'},setTimeout:fn=>{queue.push(fn);return queue.length;},clearTimeout(){}});
 vm.runInContext(source,ctx);const tool=ctx.MemBoomRemangaExporter;
 tool.capture('https://api.remanga.org/api/v2/users/12345/user_bookmarks/',{results:[{id:900,type:1,name:'Читаю'}]});
 tool.open();assert.equal(requests,0);
 assert.equal(window.fetch('https://api.remanga.org/api/v2/users/12345/bookmarks/?page=2'),originalPromise);
 nodes.get('[data-scroll]').onclick();for(let i=0;queue.length&&i<40;i++)queue.shift()();
 assert.equal(tool.snapshot().length,90);assert.ok(tool.snapshot().every(x=>x.status==='active'));assert.equal(requests,1);
 assert.equal(nodes.get('[data-scroll]').disabled,false);
 tool.capture('https://api.remanga.org/api/v2/users/12345/bookmarks/?type=900&page=4',{results:[{title:{dir:'folder-test',main_name:'Папка'},type:900}]});
 const field=nodes.get('[data-folder]');for(const part of ['Х','Хе','Хен','Хент']){field.value=part;field.oninput();}
 assert.equal(tool.snapshot().find(x=>x.url.endsWith('/folder-test')).collections.length,0);
 field.onchange();nodes.get('[data-add]').onclick();
 assert.deepEqual(Array.from(tool.snapshot().find(x=>x.url.endsWith('/folder-test')).collections),['Хент']);
});

test('manual status corrects all 62 collected DOM titles without API metadata and stays in its category',()=>{
 let offset=0,size=60;const nodes=new Map(),element=()=>({value:'',textContent:'',append(){},remove(){}});
 const panel={style:{},querySelector:s=>{if(!nodes.has(s))nodes.set(s,element());return nodes.get(s);},remove(){}};
 const doc={body:{append(){}},createElement:tag=>tag==='section'?panel:element(),querySelector:()=>null,querySelectorAll:()=>Array.from({length:size},(_,i)=>({href:`https://remanga.org/manga/scoped-${offset+i}`,getClientRects:()=>[{}],closest:()=>null,getAttribute:()=>null,querySelector:s=>s.includes('line-clamp')?{textContent:`Тайтл ${offset+i}`}:null}))};
 const location={href:'https://remanga.org/user/bookmarks?type=900',protocol:'https:',hostname:'remanga.org',pathname:'/user/bookmarks'};
 const ctx=vm.createContext({URL,document:doc,location,window:{},clearTimeout(){}});vm.runInContext(source,ctx);const tool=ctx.MemBoomRemangaExporter;
 tool.open();offset=60;size=2;nodes.get('[data-add]').onclick();assert.equal(tool.snapshot().length,62);
 nodes.get('[data-status]').value='hentai';nodes.get('[data-status]').onchange();assert.ok(tool.snapshot().every(x=>x.status==='hentai'));
 location.href='https://remanga.org/user/bookmarks?type=901';offset=100;size=1;nodes.get('[data-add]').onclick();
 nodes.get('[data-status]').value='long_read';nodes.get('[data-status]').onchange();
 assert.equal(tool.snapshot().filter(x=>x.status==='hentai').length,62);assert.equal(tool.snapshot().find(x=>x.url.endsWith('scoped-100')).status,'long_read');
});

test('updating the bookmarklet can retain the previous basket without inventing its category',()=>{
 const tool=c.createRemangaExporter();tool.restore([{title:'Собранная манга',url:'https://remanga.org/manga/saved',status:'other',favorite:true},{title:'Чужая ссылка',url:'https://evil.test/manga/x',status:'active'}]);
 assert.equal(tool.snapshot().length,1);assert.equal(tool.snapshot()[0].status,'other');assert.equal(tool.snapshot()[0].favorite,true);
});
