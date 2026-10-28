/* Observes ReManga bookmark responses/cards. Makes no network requests. */
function createRemangaExporter(){
  let basket=[],rawRows=[],installed=false,running=false,timer=null,onUpdate=()=>{},statusOverride='auto',manualFolder='';
  const folders=new Map();
  const scopeItems=new Map(),scopeStatuses=new Map(),scopeNames=new Map();
  const currentScope=()=>new URL(location.href).searchParams.get('type')??'default';
  function remember(scope,rows){if(!scopeItems.has(scope))scopeItems.set(scope,new Set());for(const row of rows)scopeItems.get(scope).add(row.url);}
  function assignStatus(scope,value){
    scopeStatuses.set(scope,value);const urls=scopeItems.get(scope)??new Set();
    for(const row of rawRows)if(row.scope===scope)row.manualStatus=value;
    basket=basket.map(row=>!urls.has(row.url)?row:value==='favorite'?{...row,favorite:true}:{...row,status:value});
  }
  function restore(rows){
    if(!Array.isArray(rows))return;
    for(const row of rows.slice(0,10000)){
      const restored=collect([{href:row.url,title:row.title}],Object.hasOwn(states,row.status)?row.status:'other');
      if(restored[0]){restored[0].favorite||=row.favorite===true;restored[0].notes=String(row.notes??'').slice(0,10000);restored[0].collections=Array.isArray(row.collections)?row.collections.filter(x=>typeof x==='string'&&x.trim()&&x.length<=100).slice(0,30):[];basket=merge(basket,restored);}
    }
  }
  const types={1:'active',2:'planned',3:'completed',4:'dropped',5:'paused',6:'not_interested',7:'favorite',8:'other'};
  const clean=value=>String(value??'').replace(/\s+/g,' ').trim();
  const meaningful=value=>{const s=clean(value);return s&&s.length<=250&&!/^(title image|image|обложка|cover|poster)$/i.test(s)?s:'';};
  const states={active:'Читаю',completed:'Прочитано',planned:'Буду читать',paused:'Отложено',dropped:'Брошено',not_interested:'Не интересно',hentai:'Хентай (хент)',yuri:'Лецуха',long_read:'Много читать (много глав)',favorite:'Любимое (без известного статуса)',other:'Без статуса'};
  const specialStatus=name=>({'не интересно':'not_interested','неинтересно':'not_interested','хент':'hentai','хентай':'hentai','лецуха':'yuri','лесбуха':'yuri','много читать':'long_read','много глав':'long_read'})[clean(name).toLowerCase().replace(/\s*\([^)]*\)\s*$/,'').trim()];
  function canonical(value){
    try{const u=new URL(value);if(u.protocol!=='https:'||!['remanga.org','www.remanga.org'].includes(u.hostname)||u.username||u.password||u.port)return null;
      const m=u.pathname.match(/^\/manga\/([^/]+)(?:\/(?:main|chapters))?\/?$/);if(!m||['top','new','catalog'].includes(m[1]))return null;return 'https://remanga.org/manga/'+m[1];
    }catch{return null;}
  }
  function collect(rows,status='other'){
    if(!Object.hasOwn(states,status))throw Error('Выберите статус списка.');
    const found=new Map();
    for(const row of rows){const url=canonical(row.href),title=meaningful(row.title);if(!url||!title)continue;
      if(!found.has(url))found.set(url,{Kind:'manga',title,url,status:status==='favorite'?'other':status,favorite:status==='favorite',progress:null,total:null});
    }return [...found.values()];
  }
  function merge(local,incoming){
    const found=new Map(local.map(x=>[x.url,{...x}]));
    for(const row of incoming){const current=found.get(row.url);if(!current)found.set(row.url,{...row});else{current.favorite||=row.favorite;if(current.status==='other'&&row.status!=='other')current.status=row.status;if(row.notes)current.notes=row.notes;
      if(row.collections?.length)current.collections=[...new Set([...(current.collections??[]),...row.collections])];}}
    return [...found.values()];
  }
  function readPage(status='other',doc=document){
    return collect(Array.from(doc.querySelectorAll('a[href*="/manga/"]')).filter(a=>a.getClientRects().length&&!a.closest('header,footer,nav,aside,dialog,#memboom-remanga-export')).map(a=>{
      const heading=a.querySelector('h2,h3,h4,[data-title],.line-clamp-2'),nested=a.querySelector('[data-id][title]');
      const title=meaningful(heading?.textContent)||meaningful(a.getAttribute('title'))||meaningful(nested?.getAttribute('title'))||meaningful(a.textContent)||meaningful(a.querySelector('img')?.getAttribute('alt'));
      return {href:a.href,title};
    }),status);
  }
  function fromResponse(payload,fallback='other'){
    const rows=Array.isArray(payload)?payload:payload?.results;if(!Array.isArray(rows))return [];
    return rows.flatMap(row=>{
      const title=row?.title;if(!title||typeof title!=='object'||!title.dir)return [];
      // A folder ID is not a reading type. title.status is publication status.
      const folder=typeof row.type==='object'?row.type:folders.get(String(row.type??row.bookmark_type));
      const status=row.manualStatus??specialStatus(folder?.name)??(row.collections??[]).map(specialStatus).find(Boolean)??(folder?types[folder.type]??'other':row.fallback??fallback);
      const items=collect([{href:'https://remanga.org/manga/'+title.dir,title:title.main_name||title.rus_name||title.en_name}],status);
      if(items[0]){
        if(folder?.name)items[0].notes='Категория ReManga: '+clean(folder.name).slice(0,200);
        items[0].collections=folder?.type===8&&folder.name?[clean(folder.name).slice(0,100)]:row.collections??[];
      }
      return items;
    });
  }
  function route(value){
    try{const u=new URL(value,'https://remanga.org');if(u.protocol!=='https:'||!['api.remanga.org','remanga.org','www.remanga.org'].includes(u.hostname)||u.port||u.username||u.password)return null;
      return /^\/api\/(?:v2\/)?users\/\d+\/(?:bookmarks|user_bookmarks)\/$/.test(u.pathname)?u:null;
    }catch{return null;}
  }
  function capture(value,payload,fallback='other',folderName='',requestScope){
    const u=route(value);if(!u)return false;
    if(u.pathname.endsWith('/user_bookmarks/')){
      const rows=Array.isArray(payload)?payload:payload?.results;if(!Array.isArray(rows))return false;
      for(const row of rows)if(row&&row.id!==undefined&&Object.hasOwn(types,row.type))folders.set(String(row.id),{type:row.type,name:clean(row.name)});
    }else{
      if(!Array.isArray(payload?.results))return false;
      const scope=requestScope??u.searchParams.get('type')??'default',batch=[];
      for(const row of payload.results)if(row?.title&&typeof row.title==='object'&&row.title.dir)batch.push({title:{dir:row.title.dir,main_name:row.title.main_name,rus_name:row.title.rus_name,en_name:row.title.en_name},type:row.type??u.searchParams.get('type'),requestType:u.searchParams.get('type'),scope,manualStatus:scopeStatuses.get(scope),fallback:Object.hasOwn(states,fallback)?fallback:'other',collections:clean(folderName)?[clean(folderName).slice(0,100)]:[]});
      rawRows.push(...batch);remember(scope,fromResponse(batch));
      if(rawRows.length>20000)rawRows=rawRows.slice(-20000);
    }
    basket=merge(fromResponse(rawRows),basket);onUpdate();return true;
  }
  function currentStatus(){
    const chosen=scopeStatuses.get(currentScope());if(chosen&&chosen!=='auto')return chosen;
    const folder=folders.get(new URL(location.href).searchParams.get('type'));if(folder)return specialStatus(folder.name)??types[folder.type]??'other';
    const selected=document.querySelector('[role="tab"][aria-selected="true"],button[data-state="active"],[aria-current="page"]');
    const label=clean(selected?.textContent).toLowerCase().replace(/\s*\(?\d+\)?\s*$/,'');
    return specialStatus(label)??Object.entries(states).find(([,name])=>name.toLowerCase().split(' (')[0]===label)?.[0]??'other';
  }
  function install(){
    if(installed)return;installed=true;
    if(typeof window.fetch==='function'){
      const original=window.fetch;
      window.fetch=function(input,init){
        const value=typeof input==='string'||input instanceof URL?String(input):input?.url;
        const method=String(init?.method??input?.method??'GET').toUpperCase(),scope=currentScope(),fallback=requestStatus(value),folderName=requestFolder(value),promise=original.apply(this,arguments);
        if(method==='GET'&&route(value))promise.then(response=>{if(response.ok)response.clone().json().then(payload=>capture(value,payload,fallback,folderName,scope)).catch(()=>{});},()=>{});
        return promise;
      };
    }
    if(typeof XMLHttpRequest!=='undefined'){
      const proto=XMLHttpRequest.prototype,original=proto.open;
      proto.open=function(method,value){
        const scope=currentScope(),fallback=requestStatus(value),folderName=requestFolder(value),result=original.apply(this,arguments);
        if(String(method).toUpperCase()==='GET'&&route(value))this.addEventListener('load',()=>{
          try{if(this.status>=200&&this.status<300)capture(value,this.responseType==='json'?this.response:JSON.parse(this.responseText),fallback,folderName,scope);}catch{}
        },{once:true});return result;
      };
    }
  }
  function stop(){running=false;if(timer!==null)clearTimeout(timer);timer=null;onUpdate();}
  function requestStatus(value){
    if(!route(value))return 'other';return scopeStatuses.get(currentScope())??'other';
  }
  function currentFolder(){const folder=folders.get(new URL(location.href).searchParams.get('type'));return scopeNames.get(currentScope())||(folder?.type===8?folder.name:'');}
  function requestFolder(value){const u=route(value);return u&&u.searchParams.get('type')===new URL(location.href).searchParams.get('type')?currentFolder():'';}
  function open(){
    if(location.protocol!=='https:'||!['remanga.org','www.remanga.org'].includes(location.hostname)||!/^\/user\/bookmarks\/?$/.test(location.pathname)){
      alert('Откройте https://remanga.org/user/bookmarks в браузере, где выполнен вход в ReManga, затем снова нажмите закладку MemBoom.');return;
    }
    install();stop();document.querySelector('#memboom-remanga-export')?.remove();
    const dialog=document.createElement('section');dialog.id='memboom-remanga-export';
    dialog.style.cssText='position:fixed;z-index:2147483647;right:16px;top:16px;max-width:440px;width:calc(100% - 32px);max-height:85vh;overflow:auto;box-sizing:border-box;border:1px solid #a8425a;border-radius:18px;background:#1c1525;color:#f9eefa;padding:20px;font:14px/1.5 system-ui;box-shadow:0 12px 60px #0009';
    dialog.innerHTML='<h2 style="margin-top:0">ReManga → MemBoom · версия 4</h2><p>Оставь окно открытым. Переключи категорию ReManga, чтобы загрузить её данные. Страницу не обновляй.</p><label>Статус списка <select data-status><option value="auto">Определять по категории</option></select></label><p><label>Название статуса ReManga <input data-folder maxlength="100" placeholder="Хентай, Лецуха, Много читать"></label><small style="display:block">Если статус не определился, выбери его выше или укажи название. Перед следующей категорией измени или очисти поле.</small></p><p data-count></p><div style="display:flex;gap:8px;flex-wrap:wrap"><button data-scroll>Собрать категорию прокруткой</button><button data-stop>Стоп</button><button data-add>Добавить видимые</button><button data-save>Скачать JSON</button><button data-close>Свернуть</button></div><p data-message role="status"></p><p style="font-size:12px">Собери все категории по очереди. Сверь количество с ReManga. Если статус не определился, выбери его вручную перед сбором. Пароли и токены не читаются; новые запросы не отправляются. Последняя глава не угадывается.</p>';
    dialog.querySelector('h2').textContent='ReManga → MemBoom · версия 5';
    const status=dialog.querySelector('[data-status]'),count=dialog.querySelector('[data-count]'),message=dialog.querySelector('[data-message]');
    for(const [value,label]of Object.entries(states)){const option=document.createElement('option');option.value=value;option.textContent=label;status.append(option);}status.value=statusOverride;
    const refresh=()=>{const scope=currentScope();if(visibleScope!==scope){visibleScope=scope;folderInput.value=scopeNames.get(scope)??'';}status.value=scopeStatuses.get(scope)??'auto';count.textContent=`Собрано: ${basket.length}. В текущей категории: ${scopeItems.get(scope)?.size??0}. Без статуса: ${basket.filter(x=>x.status==='other'&&!x.favorite).length}. ${folders.size?'Категорий из API: '+folders.size+'.':'API не передал категории — выбери статус вручную.'} ${running?'Идёт прокрутка…':''}`;dialog.querySelector('[data-save]').disabled=!basket.length;dialog.querySelector('[data-scroll]').disabled=running;};
    onUpdate=refresh;
    const add=()=>{const name=currentFolder(),category=new URL(location.href).searchParams.get('type');
      if(name){for(const row of rawRows)if(row.requestType===category)row.collections=[name];basket=merge(fromResponse(rawRows),basket);}
      const rows=readPage(specialStatus(name)??currentStatus()).map(x=>({...x,collections:name?[name]:[]}));remember(currentScope(),rows);
      basket=merge(basket,rows);const chosen=scopeStatuses.get(currentScope());if(chosen&&chosen!=='auto')assignStatus(currentScope(),chosen);refresh();};
    const folderInput=dialog.querySelector('[data-folder]');let visibleScope=currentScope();folderInput.value=scopeNames.get(visibleScope)??'';
    folderInput.oninput=stop;
    folderInput.onchange=()=>{stop();manualFolder=clean(folderInput.value);scopeNames.set(currentScope(),manualFolder);const inferred=specialStatus(manualFolder);if(inferred)assignStatus(currentScope(),inferred);refresh();};
    status.onchange=()=>{const value=status.value;stop();statusOverride=value;
      if(statusOverride!=='auto'){
        const category=new URL(location.href).searchParams.get('type');
        for(const row of rawRows)if(row.requestType===category)row.fallback=statusOverride;
        basket=merge(fromResponse(rawRows),basket);assignStatus(currentScope(),statusOverride);
        message.textContent=`Статус применён к текущей категории (${scopeItems.get(currentScope())?.size??0} тайтлов).`;
      }else{scopeStatuses.delete(currentScope());for(const row of rawRows)if(row.scope===currentScope())delete row.manualStatus;}refresh();};dialog.querySelector('[data-add]').onclick=add;dialog.querySelector('[data-close]').onclick=()=>{stop();dialog.remove();};
    dialog.querySelector('[data-stop]').onclick=()=>{stop();message.textContent='Прокрутка остановлена. Собранное можно скачать.';};
    dialog.querySelector('[data-scroll]').onclick=()=>{
      stop();running=true;let idle=0,steps=0,lastSize=-1,lastHeight=-1;
      const category=new URL(location.href).searchParams.get('type');
      const tick=()=>{
        if(!running)return;
        if(new URL(location.href).searchParams.get('type')!==category){stop();message.textContent='Категория изменилась. Запусти сбор новой категории.';return;}
        add();const height=document.scrollingElement.scrollHeight;
        const atBottom=window.scrollY+window.innerHeight>=height-10;
        idle=atBottom&&basket.length===lastSize&&height===lastHeight?idle+1:0;lastSize=basket.length;lastHeight=height;
        if(idle>=12||++steps>=600){stop();message.textContent='Новые карточки больше не появились. Сверь количество с ReManga; при необходимости запусти сбор ещё раз. Затем выбери следующую категорию.';return;}
        window.scrollBy(0,Math.max(300,window.innerHeight*.8));refresh();timer=setTimeout(tick,1000);
      };
      window.scrollTo(0,0);tick();
    };
    dialog.querySelector('[data-save]').onclick=()=>{
      stop();add();if(!basket.length)return;
      const value={format:'memboom-library-v1',source:'https://remanga.org/user/bookmarks',capturedAt:new Date().toISOString(),scope:'collected-categories-check-counts',entries:basket};
      const blob=new Blob([JSON.stringify(value,null,2)],{type:'application/json'}),href=URL.createObjectURL(blob),link=document.createElement('a');
      link.href=href;link.download='remanga-memboom.json';document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(href),1000);
      message.textContent='Файл скачан. В MemBoom: «Манга → Перенести список» → выберите remanga-memboom.json.';
    };
    document.body.append(dialog);add();refresh();
  }
  function bookmarklet(){return 'javascript:'+encodeURIComponent('(()=>{let tool=window.__memboomRemangaExportV5Scoped;if(!tool){const old=window.__memboomRemangaExportV4Aliases||window.__memboomRemangaExportV4||window.__memboomRemangaExportV3||window.__memboomRemangaExportV2;tool=window.__memboomRemangaExportV5Scoped=('+createRemangaExporter.toString()+')();if(old){old.stop?.();tool.restore(old.snapshot?.());}}tool.open();})()');}
  return {collect,merge,readPage,fromResponse,capture,restore,snapshot:()=>basket.map(x=>({...x})),open,stop,bookmarklet};
}
globalThis.MemBoomRemangaExporter=createRemangaExporter();
