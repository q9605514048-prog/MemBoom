/* Opt-in read-only following of a public profile. The vault remains the writer. */
globalThis.MemBoomAnimeFollow=(()=>{
 const tr=(key,...args)=>globalThis.MemBoomI18n?.t(key,...args)??key.replace(/\{(\d+)\}/g,(_,n)=>String(args[+n]??''));
 const identity=value=>{const u=new URL(value);return u.hostname==='yummyanime.tv'?u.pathname.match(/^\/(\d+)-/)?.[1]:null;};
 function mergeProfile(local,remote){
  if(remote?.complete!==true||!Array.isArray(remote.items))throw Error('Incomplete profile');
  const model=globalThis.MemBoomLibrary,incoming=model.importRecords(remote.items,'anime'),items=local.map(x=>({...x}));model.validate(items);let added=0,updated=0;
  for(const row of incoming){const id=identity(row.Url),old=items.find(x=>x.Kind==='anime'&&identity(x.Url)===id);if(!old){items.push(row);added++;continue;}
   const next={...old,Title:row.Title,Url:row.Url,Favorite:row.Favorite,CoverUrl:row.CoverUrl||old.CoverUrl,Status:row.Status==='other'?old.Status:row.Status,StatusEdited:false};
   if(['Title','Url','Favorite','CoverUrl','Status'].some(key=>next[key]!==old[key])){Object.assign(old,next,{UpdatedAt:new Date().toISOString()});updated++;}
  }model.validate(items);return {items,added,updated};
 }
 function createFollower({fetchProfile,apply,isUnlocked,isVisible,isOnline,status,schedule=setTimeout,cancel=clearTimeout}){
  let generation=0,profile=null,timer=null,pending=null;
  function stop(){generation++;profile=null;cancel(timer);pending?.controller.abort();pending=null;}
  async function refresh(){
   if(!profile||!isUnlocked()||pending)return;
   const epoch=generation;
   if(!isVisible()||!isOnline()){status(isOnline()?'paused':'offline');timer=schedule(refresh,60000);return;}
   const token={controller:new AbortController()};pending=token;status('loading');
   try{const data=await fetchProfile(profile,token.controller.signal);if(epoch!==generation||!isUnlocked())return;const changes=await apply(data);if(epoch===generation&&isUnlocked())status('current',{...changes,checkedAt:data.checkedAt});}
   catch{if(epoch===generation)status('error');}
   finally{if(pending===token)pending=null;if(epoch===generation&&profile){cancel(timer);timer=schedule(refresh,60000);}}
  }
  function start(value){stop();profile=value;void refresh();}
  return {start,stop,refresh};
 }
 function validProfile(value){const u=new URL(value);if(u.origin!=='https://yummyanime.tv'||u.username||u.password||u.port||u.search||u.hash||!/^\/user\/id[1-9]\d{0,11}\/?$/.test(u.pathname))throw Error('Invalid profile');return u.origin+u.pathname.replace(/\/$/,'');}
 function mount({getItems,isUnlocked,change,endpoint}){
  const storageKey='memboom.anime-follow.v1';let settings={profile:'',enabled:false},configured=false,active=false,lastChecked=null;
  try{const saved=JSON.parse(localStorage.getItem(storageKey)||'null');if(saved?.profile)settings={profile:validProfile(saved.profile),enabled:saved.enabled===true};}catch{}
  const panel=document.createElement('section');panel.id='animeFollow';panel.className='anime-follow';panel.hidden=true;
  panel.innerHTML=`<div class="anime-follow-header"><div><strong>${tr('Обновления из YummyAnime')}</strong><p class="hint" data-follow-status role="status" aria-live="polite"></p></div><button class="btn small" type="button" data-refresh>${tr('Проверить сейчас')}</button></div><details><summary>${tr('Настроить профиль')}</summary><form class="form"><label>${tr('Ссылка на публичный профиль')}<input name="profile" type="url" placeholder="https://yummyanime.tv/user/id…" required autocomplete="off"></label><label class="follow-switch"><input name="enabled" type="checkbox">${tr('Автоматически обновлять раз в минуту')}</label><p class="hint">${tr('Проверка работает, пока сайт открыт. Статусы и избранное берутся из профиля; ваши заметки и прогресс сохраняются. Удаление на источнике не удаляет локальную запись.')}</p><button class="btn" type="submit">${tr('Подключить профиль')}</button><p class="error-message" data-follow-error role="alert"></p></form></details>`;
  document.querySelector('#libraryPanel .library-heading').after(panel);
  const form=panel.querySelector('form'),message=panel.querySelector('[data-follow-status]'),refreshButton=panel.querySelector('[data-refresh]'),details=panel.querySelector('details');
  function fields(){form.elements.profile.value=settings.profile;form.elements.enabled.checked=settings.enabled;details.open=!settings.profile;refreshButton.disabled=!settings.enabled;}
  function status(state,meta={}){
   refreshButton.disabled=state==='loading'||!settings.enabled;
   message.textContent=state==='loading'?tr('Проверяем профиль…'):state==='current'?(lastChecked=meta.checkedAt,tr('Проверено в {0}. Добавлено: {1}, обновлено: {2}.',new Date(meta.checkedAt).toLocaleTimeString(globalThis.MemBoomI18n?.language??'ru',{hour:'2-digit',minute:'2-digit'}),meta.added??0,meta.updated??0)):state==='error'?tr('Не удалось обновить профиль. Локальная медиатека сохранена; повторим через минуту.'):state==='offline'?tr('Нет сети. Автообновление продолжится после подключения.'):state==='paused'?tr('Проверка приостановлена, пока вкладка скрыта.'):state==='api'?tr('Для автообновления нужен MemBoom API. Запустите локальный сервер или подключите свой HTTPS API в настройках синхронизации.'):settings.profile?tr('Автообновление выключено.'):tr('Подключите публичный профиль, чтобы следить за изменениями списков.');
  }
  function apiUrl(path){const base=endpoint();return new URL(path,base).href;}
  const loop=createFollower({
   fetchProfile:async(profile,signal)=>{const response=await fetch(apiUrl('/api/library/anime-profile?url='+encodeURIComponent(profile)),{signal,credentials:'omit',cache:'no-store'});if(!response.ok)throw Error('Source unavailable');return response.json();},
   apply:async remote=>{const result=mergeProfile(getItems(),remote);if(result.added||result.updated){const ok=await change(items=>{const merged=mergeProfile(items,remote);items.splice(0,items.length,...merged.items);});if(!ok)throw Error('Vault unavailable');}return {added:result.added,updated:result.updated};},
   isUnlocked,isVisible:()=>document.visibilityState!=='hidden',isOnline:()=>navigator.onLine!==false,status
  });
  function save(){localStorage.setItem(storageKey,JSON.stringify(settings));}
  function connect(){fields();if(settings.enabled&&settings.profile&&isUnlocked()){details.open=false;loop.start(settings.profile);}else{loop.stop();status('off');}}
  form.onsubmit=e=>{e.preventDefault();try{settings={profile:validProfile(form.elements.profile.value.trim()),enabled:form.elements.enabled.checked};save();panel.querySelector('[data-follow-error]').textContent='';connect();}catch{panel.querySelector('[data-follow-error]').textContent=tr('Укажите публичный профиль вида https://yummyanime.tv/user/id123.');}};
  form.elements.enabled.onchange=()=>{if(!form.elements.enabled.checked){settings.enabled=false;save();loop.stop();status('off');refreshButton.disabled=true;}};
  refreshButton.onclick=()=>loop.refresh();
  async function wake(){
   if(!isUnlocked())return;active=true;
   if(!configured){configured=true;try{const response=await fetch(apiUrl('/api/library/anime-source'),{cache:'no-store',credentials:'omit'});if(!response.ok)throw Error('API unavailable');const value=await response.json();if(!settings.profile&&value.profile){settings={profile:validProfile(value.profile),enabled:true};save();}}catch{if(!settings.profile)status('api');fields();return;}}
   if(active)connect();
  }
  function clear(){active=false;configured=false;lastChecked=null;loop.stop();message.textContent='';}
  document.addEventListener('visibilitychange',()=>{if(document.visibilityState!=='hidden'&&active)void loop.refresh();});
  window.addEventListener('online',()=>{if(active)void loop.refresh();});
  fields();status('off');return {wake,clear,setVisible(value){panel.hidden=!value;if(value&&!active)void wake();}};
 }
 return {mergeProfile,createFollower,mount};
})();
