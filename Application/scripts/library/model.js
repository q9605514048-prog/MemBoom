globalThis.MemBoomLibrary=(()=>{
  const statuses=['planned','active','completed','paused','dropped','not_interested','hentai','yuri','long_read','other'];
  const labels={planned:'В планах',active:'В процессе',completed:'Завершено',paused:'Отложено',dropped:'Брошено',not_interested:'Не интересно',hentai:'Хентай',yuri:'Лецуха',long_read:'Много читать',other:'Без статуса'};
  const mangaAliases={'не интересно':'not_interested','неинтересно':'not_interested','хент':'hentai','хентай':'hentai',hentai:'hentai','лецуха':'yuri','лесбуха':'yuri',yuri:'yuri','много читать':'long_read','много глав':'long_read',long_read:'long_read'};
  const customStatus=value=>mangaAliases[String(value??'').replace(/\s+/g,' ').trim().toLowerCase().replace(/\s*\([^)]*\)\s*$/,'').trim()];
  const alias={planned:'planned',active:'active',completed:'completed',paused:'paused',dropped:'dropped',other:'other',watching:'active',reading:'active',on_hold:'paused','смотрю':'active','читаю':'active','просмотрено':'completed','прочитано':'completed','завершено':'completed','запланировано':'planned','буду читать':'planned','буду смотреть':'planned','в планах':'planned','отложено':'paused','брошено':'dropped','1':'active','2':'completed','3':'planned','4':'paused','5':'dropped'};
  function url(value,kind){
    const u=new URL(String(value).trim());
    const host=u.hostname.toLowerCase().replace(/^www\./,'');
    if(u.protocol!=='https:'||u.username||u.password||u.port||!['remanga.org','yummyanime.tv'].includes(host))throw Error('Нужна ссылка HTTPS на ReManga или YummyAnime.');
    if(kind==='manga'&&(host!=='remanga.org'||!/^\/manga\/[^/]+(?:\/(?:main|chapters))?\/?$/.test(u.pathname)))throw Error('Для манги укажите страницу тайтла ReManga.');
    if(kind==='anime'&&(host!=='yummyanime.tv'||!/^\/\d+-[^/]+\.html$/.test(u.pathname)))throw Error('Для аниме укажите страницу тайтла YummyAnime.');
    u.hostname=host;u.hash='';u.search='';if(kind==='manga')u.pathname=u.pathname.replace(/\/(?:main|chapters)\/?$/,'').replace(/\/$/,'');return u.href;
  }
  function coverUrl(value){
    if(!value)return '';const u=new URL(String(value));
    if(u.protocol!=='https:'||u.username||u.password||u.port||!((['remanga.org','api.remanga.org'].includes(u.hostname)&&u.pathname.startsWith('/media/'))||(u.hostname==='yummyanime.tv'&&/^\/(static\/posters|uploads)\//.test(u.pathname))))throw Error('Нужна ссылка на обложку ReManga или YummyAnime.');
    if(u.hostname==='yummyanime.tv')u.pathname=u.pathname.replace(/^(\/static\/posters\/)\d+x\d+\//,'$1').replace('/uploads/posts/thumbs/','/uploads/posts/');
    return u.href;
  }
  function collections(value){
    if(value===undefined||value===null||value==='')return [];
    const list=typeof value==='string'?value.split(','):value;
    if(!Array.isArray(list)||list.length>30||list.some(x=>typeof x!=='string'||!x.trim()||x.trim().length>100))throw Error('Названия папок — до 100 символов, не больше 30 папок на тайтл.');
    return [...new Set(list.map(x=>x.trim()))];
  }
  function validate(items){
    if(!Array.isArray(items)||items.length>10000)throw Error('Список должен содержать не более 10 000 тайтлов.');
    const ids=new Set();
    for(const x of items){
      if(!x||typeof x.Id!=='string'||!x.Id||x.Id.length>128||ids.has(x.Id)||!['anime','manga'].includes(x.Kind)||typeof x.Title!=='string'||!x.Title.trim()||x.Title.length>250||!statuses.includes(x.Status)||typeof x.Favorite!=='boolean'||typeof x.Notes!=='string'||x.Notes.length>10000||!Number.isFinite(Date.parse(x.UpdatedAt)))throw Error('Некорректная запись в списке.');
      url(x.Url,x.Kind);if(x.CoverUrl!==undefined)coverUrl(x.CoverUrl);if(x.Collections!==undefined)collections(x.Collections);ids.add(x.Id);
      for(const n of [x.Progress,x.Total])if(n!==null&&(!Number.isFinite(n)||n<0||n>1000000||(x.Kind==='anime'&&!Number.isInteger(n))))throw Error('Проверьте номер главы или серии.');
      if(x.Total!==null&&x.Progress!==null&&x.Progress>x.Total)throw Error('Прогресс больше общего количества.');
    }return items;
  }
  const numeric=value=>value===undefined||value===null||value===''?null:Number(value);
  function importRecords(rows,kind){
    if(!Array.isArray(rows)||rows.length>10000)throw Error('Ожидается список тайтлов (до 10 000).');
    const result=new Map();
    for(const row of rows){
      const rawStatus=String(row.Status??row.status??row.list??'other').trim().toLowerCase();
      const favorite=row.Favorite===true||row.favorite===true||row.favorite==='true'||rawStatus==='любимое'||rawStatus==='6';
      const value={Id:String(row.Id??crypto.randomUUID()),Kind:row.Kind??kind,Title:String(row.Title??row.title??row.name??'').trim(),Url:'',Status:alias[rawStatus]??'other',Progress:numeric(row.Progress??row.progress),Total:numeric(row.Total??row.total),Favorite:favorite,Notes:String(row.Notes??row.notes??''),UpdatedAt:row.UpdatedAt??new Date().toISOString()};
      value.CoverUrl=coverUrl(row.CoverUrl??row.coverUrl??'');
      const oldFolder=value.Notes.match(/^Категория ReManga: (.+)$/)?.[1];
      const standardFolder=oldFolder&&['читаю','буду читать','прочитано','отложено','брошено','любимое','не интересно','неинтересно'].includes(oldFolder.toLowerCase());
      value.Collections=collections(row.Collections??row.collections??(oldFolder&&!standardFolder?[oldFolder]:[]));
      if(['not_interested','не интересно','неинтересно'].includes(rawStatus))value.Status='not_interested';
      if(value.Kind==='manga')value.Status=customStatus(rawStatus)??(row.StatusVersion!==1?(value.Collections.map(customStatus).find(Boolean)??customStatus(oldFolder)):undefined)??value.Status;
      value.StatusVersion=1;
      value.StatusEdited=row.StatusEdited===true;
      value.Url=url(row.Url??row.url,value.Kind);validate([value]);
      const key=value.Kind+':'+value.Url,existing=result.get(key);
      if(existing){existing.Favorite||=favorite;if(existing.Status==='other'&&value.Status!=='other')existing.Status=value.Status;existing.Collections=collections([...(existing.Collections??[]),...value.Collections]);if(!existing.CoverUrl&&value.CoverUrl)existing.CoverUrl=value.CoverUrl;}
      else result.set(key,value);
    }return [...result.values()];
  }
  function migrate(items){
    validate(items);return items.map(x=>{
      if(x.Kind!=='manga'||x.StatusVersion===1)return {...x};
      const oldFolder=x.Notes.match(/^Категория ReManga: (.+)$/)?.[1];
      const status=(x.Collections??[]).map(customStatus).find(Boolean)??customStatus(oldFolder);
      return {...x,Status:status??x.Status,StatusVersion:1};
    });
  }
  function merge(local,incoming){
    validate(local);validate(incoming);const items=local.map(x=>({...x}));const known=new Map(items.map(x=>[x.Kind+':'+url(x.Url,x.Kind),x]));let added=0,skipped=0,updated=0;
    for(const value of incoming){const key=value.Kind+':'+url(value.Url,value.Kind),existing=known.get(key);if(existing){
      let changed=false;
      if(existing.Kind==='manga'&&/^title image$/i.test(existing.Title.trim())&&!/^title image$/i.test(value.Title.trim())){existing.Title=value.Title;changed=true;}
      if((existing.Status==='other'&&value.Status!=='other')||(!existing.StatusEdited&&!['not_interested','hentai','yuri','long_read'].includes(existing.Status)&&['not_interested','hentai','yuri','long_read'].includes(value.Status))){existing.Status=value.Status;changed=true;}
      if(!existing.Favorite&&value.Favorite){existing.Favorite=true;changed=true;}
      if(!existing.Notes&&value.Notes){existing.Notes=value.Notes;changed=true;}
      const combined=collections([...(existing.Collections??[]),...(value.Collections??[])]);
      if(JSON.stringify(combined)!==JSON.stringify(existing.Collections??[])){existing.Collections=combined;changed=true;}
      if(!existing.CoverUrl&&value.CoverUrl){existing.CoverUrl=value.CoverUrl;changed=true;}
      if(changed){existing.UpdatedAt=new Date().toISOString();updated++;}else skipped++;continue;
    }const entry={...value,Id:items.some(x=>x.Id===value.Id)?crypto.randomUUID():value.Id};items.push(entry);known.set(key,entry);added++;}
    validate(items);return {items,added,skipped,updated};
  }
  function csv(text){
    const rows=[];let row=[],cell='',quoted=false;
    for(let i=0;i<text.length;i++){const c=text[i];if(c==='"'){if(quoted&&text[i+1]==='"'){cell+='"';i++;}else quoted=!quoted;}
      else if(c===','&&!quoted){row.push(cell);cell='';}else if(c==='\n'&&!quoted){row.push(cell.replace(/\r$/,''));rows.push(row);row=[];cell='';}else cell+=c;}
    if(quoted)throw Error('Незакрытая кавычка в CSV.');if(cell||row.length){row.push(cell.replace(/\r$/,''));rows.push(row);}
    const keys=(rows.shift()??[]).map(x=>x.trim().toLowerCase());if(!keys.includes('url'))throw Error('В CSV нужны столбцы title,url,status,progress.');
    return rows.filter(r=>r.some(Boolean)).map(r=>Object.fromEntries(keys.map((k,i)=>[k,r[i]??''])));
  }
  function parseFile(text,kind){
    if(typeof text!=='string'||text.length>4*1024*1024)throw Error('Размер файла — до 4 МБ.');text=text.replace(/^\uFEFF/,'').trim();
    if(!text)throw Error('Список пуст.');
    if(/^[\[{]/.test(text)){const data=JSON.parse(text);return importRecords(Array.isArray(data)?data:data.entries??data.library,kind);}
    if(/^title,|^url,|^name,/i.test(text))return importRecords(csv(text),kind);
    return importRecords(text.split(/\r?\n/).filter(x=>x.trim()).map(line=>{const found=line.match(/https:\/\/[^\s<>"']+/);if(!found)throw Error('В каждой строке нужна ссылка на тайтл.');return {url:found[0],title:line.replace(found[0],'').replace(/^[\s\t|—-]+|[\s\t|—-]+$/g,'')||decodeURIComponent(new URL(found[0]).pathname.split('/').filter(Boolean).find(p=>p!=='manga')??'Тайтл')};}),kind);
  }
  function advance(item){
    const progress=(item.Progress??0)+1;if(item.Total!==null&&progress>item.Total)throw Error('Достигнуто общее количество.');
    const status=['hentai','yuri','long_read'].includes(item.Status)?item.Status:item.Total===progress?'completed':'active';
    return validate([{...item,Progress:progress,Status:status,StatusEdited:true,UpdatedAt:new Date().toISOString()}])[0];
  }
  function filter(items,kind,query='',status='',favorites=false,folder=''){const q=query.trim().toLocaleLowerCase('ru');return items.filter(x=>x.Kind===kind&&(!status||x.Status===status)&&(!favorites||x.Favorite)&&(!folder||(x.Collections??[]).includes(folder))&&(!q||(x.Title+' '+x.Notes+' '+(x.Collections??[]).join(' ')).toLocaleLowerCase('ru').includes(q))).sort((a,b)=>Number(b.Favorite)-Number(a.Favorite)||Date.parse(b.UpdatedAt)-Date.parse(a.UpdatedAt));}
  return {statuses,labels,url,coverUrl,collections,customStatus,migrate,advance,validate,importRecords,merge,parseFile,filter};
})();
