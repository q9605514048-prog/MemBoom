globalThis.MemBoomLibraryUI=(()=>{
const tr=(key,...args)=>globalThis.MemBoomI18n?.t(key,...args)??key.replace(/\{(\d+)\}/g,(_,n)=>String(args[+n]??''));

  const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function mount({getItems,isUnlocked,change,modal,toast}){
    const model=MemBoomLibrary;
    let kind='anime',favoriteOnly=false,generation=0,folder='';
    const covers=globalThis.MemBoomLibraryCovers?.create(isUnlocked);
    const grid=document.querySelector('#libraryList'),query=document.querySelector('#librarySearch'),status=document.querySelector('#libraryStatus');
    const folderBar=document.createElement('div');folderBar.className='library-folders';folderBar.setAttribute('aria-label',tr("Мои папки манги"));grid.before(folderBar);
    const availableStatuses=()=>model.statuses.filter(s=>!['not_interested','hentai','yuri','long_read'].includes(s)||kind==='manga');
    function label(s){return s==='active'?(kind==='manga'?tr("Читаю"):tr("Смотрю")):s==='completed'?(kind==='manga'?tr("Прочитано"):tr("Просмотрено")):tr(model.labels[s]);}
    status.innerHTML=`<option value="">${tr("Все статусы")}</option>`+availableStatuses().map(s=>`<option value="${s}">${esc(label(s))}</option>`).join('');
    function render(){
      const all=isUnlocked()?getItems().filter(x=>x.Kind===kind):[];
      const visible=model.filter(all,kind,query.value,status.value,favoriteOnly,folder).map(x=>({...x,Collections:[]}));
      document.querySelector('#libraryTitle').textContent=kind==='manga'?tr("Моя манга"):tr("Моё аниме");
      document.querySelector('#librarySubtitle').textContent=kind==='manga'?tr("Главы, к которым хочется вернуться."):tr("Истории, которые остаются с тобой.");
      document.querySelector('#libraryCount').textContent=tr("Показано: {0} из {1}", visible.length, all.length);
      document.querySelector('#libraryPanel > .hint').textContent=tr("Коллекция зашифрована в этом браузере. Для её переноса используй полную копию сайта: сетевая синхронизация пока передаёт только аккаунты. Списки на исходных сайтах не изменяются.");
      document.querySelector('#librarySummary').innerHTML=availableStatuses().map(s=>`<button class="library-stat" data-status="${s}" aria-pressed="${status.value===s}"><strong>${all.filter(x=>x.Status===s).length}</strong><span>${esc(label(s))}</span></button>`).join('');
      document.querySelector('#librarySummary').dataset.kind=kind;
      folderBar.hidden=true;folderBar.innerHTML='';
      document.querySelector('#libraryFavorite').setAttribute('aria-pressed',String(favoriteOnly));
      grid.innerHTML=visible.map(x=>`<article class="library-card">
        <div class="library-poster"><span class="library-cover-placeholder" aria-hidden="true">${kind==='manga'?'▤':'▶'}</span><img data-library-cover="${esc(x.Id)}" alt="${tr("Обложка: {0}", esc(x.Title))}" loading="lazy" decoding="async" referrerpolicy="no-referrer" hidden><button class="library-cover-retry" data-cover-retry hidden type="button" aria-label="${tr("Повторить загрузку обложки: {0}", esc(x.Title))}">${tr("↻ Повторить")}</button><span class="library-poster-status">${esc(label(x.Status))}</span><button class="library-cover-favorite" data-library-action="favorite" data-id="${esc(x.Id)}" aria-label="${tr("Избранное: {0}", esc(x.Title))}" aria-pressed="${x.Favorite}">${x.Favorite?'★':'☆'}</button></div>
        <div class="library-card-body"><h3>${esc(x.Title)}</h3>${(x.Collections??[]).length?`<div class="library-card-folders">${x.Collections.map(name=>`<span>${esc(name)}</span>`).join('')}</div>`:''}<p class="library-progress">${kind==='manga'?tr("Глава"):tr("Серия")} <strong>${x.Progress===null?tr("не указана"):esc(x.Progress)}</strong>${x.Total===null?'':` / ${esc(x.Total)}`}</p>${x.Notes?`<p class="library-notes">${esc(x.Notes)}</p>`:''}<div class="library-card-actions"><a class="btn small" href="${esc(model.url(x.Url,kind))}" target="_blank" rel="noopener noreferrer">${kind==='manga'?tr("Читать"):tr("Смотреть")} ↗</a><button class="btn small" data-library-action="increment" data-id="${esc(x.Id)}" aria-label="${tr("Следующая {0}: {1}", kind==='manga'?tr("глава"):tr("серия"), esc(x.Title))}">+1</button><button class="copy" data-library-action="edit" data-id="${esc(x.Id)}">${tr("Изменить")}</button><button class="copy" data-library-action="delete" data-id="${esc(x.Id)}" aria-label="${tr("Удалить {0}", esc(x.Title))}">×</button></div></div></article>`).join('')||`<div class="empty-state"><span class="empty-symbol">${kind==='manga'?'▤':'▶'}</span><h2>${folder?tr("В папке пока нет тайтлов"):all.length?tr("Ничего не найдено"):tr("Твоя коллекция начинается здесь")}</h2><p>${folder?tr("Укажи эту папку в форме тайтла или перенеси её из ReManga."):all.length?tr("Измени поиск или выбери другой статус."):tr("Импортируй свой список или добавь первый тайтл по ссылке.")}</p></div>`;
      covers?.show(grid,visible);
    }
    function setKind(value){kind=value;query.value='';status.value='';favoriteOnly=false;folder='';status.innerHTML=`<option value="">${tr("Все статусы")}</option>`+availableStatuses().map(s=>`<option value="${s}">${esc(label(s))}</option>`).join('');render();}
    function close(){generation++;modal.innerHTML='';}
    function edit(item){
      if(!isUnlocked())return;const lease=++generation;
      modal.innerHTML=`<div class="overlay"><form class="dialog library-editor" role="dialog" aria-modal="true"><h2>${item?tr("Изменить тайтл"):tr("Добавить ")+(kind==='manga'?tr("мангу"):tr("аниме"))}</h2><div class="form"><label>${tr("Название")}<input name="title" maxlength="250" value="${esc(item?.Title??'')}" required></label><label>${tr("Ссылка на {0}", kind==='manga'?'ReManga':'YummyAnime')}<input name="url" type="url" value="${esc(item?.Url??'')}" required placeholder="https://${kind==='manga'?'remanga.org/manga/…':'yummyanime.tv/…html'}"></label><label>${tr("Статус")}<select name="status">${model.statuses.map(s=>`<option value="${s}" ${s===(item?.Status??'planned')?'selected':''}>${esc(label(s))}</option>`).join('')}</select></label><div class="library-numbers"><label>${kind==='manga'?tr("Прочитано глав"):tr("Просмотрено серий")}<input name="progress" type="number" min="0" max="1000000" step="${kind==='manga'?'0.1':'1'}" value="${item?.Progress??''}" placeholder="${tr("Неизвестно")}"></label><label>${tr("Всего")}<input name="total" type="number" min="0" max="1000000" step="${kind==='manga'?'0.1':'1'}" value="${item?.Total??''}" placeholder="${tr("Неизвестно")}"></label></div><label>${tr("Заметка")}<textarea name="notes" maxlength="10000" rows="3">${esc(item?.Notes??'')}</textarea></label></div><p data-library-error class="auth-error" role="alert"></p><div class="dialog-actions"><button class="btn" type="button" data-cancel>${tr("Отмена")}</button><button class="btn primary">${tr("Сохранить тайтл")}</button></div></form></div>`;
      const form=modal.querySelector('form');form.querySelector('[data-cancel]').onclick=close;
      const choices=form.querySelector('[name="status"]');choices.innerHTML=availableStatuses().map(s=>`<option value="${s}" ${s===(item?.Status??'planned')?'selected':''}>${esc(label(s))}</option>`).join('');
      form.onsubmit=async event=>{
        event.preventDefault();if(lease!==generation||!isUnlocked())return;
        const values=new FormData(form);let entry;
        try{entry=model.importRecords([{Id:item?.Id,Kind:kind,Title:values.get('title'),Url:values.get('url'),Status:values.get('status'),StatusVersion:1,StatusEdited:!item||item.StatusEdited===true||item.Status!==values.get('status'),Progress:values.get('progress'),Total:values.get('total'),Notes:values.get('notes'),Favorite:item?.Favorite??false,CoverUrl:item?.CoverUrl??'',Collections:item?.Collections??[]}],kind)[0];}
        catch(error){form.querySelector('[data-library-error]').textContent=error.message;return;}
        const ok=await change(items=>{if(items.some(x=>x.Id!==entry.Id&&model.url(x.Url,x.Kind)===entry.Url))throw Error(tr("Этот тайтл уже есть в списке."));const i=items.findIndex(x=>x.Id===entry.Id);if(i<0)items.push(entry);else items[i]=entry;});
        if(ok&&lease===generation)close();
      };form.querySelector('[name="title"]').focus();
    }
    function showImport(){
      if(!isUnlocked())return;const lease=++generation;let pending=[],readRevision=0;
      modal.innerHTML=`<div class="overlay"><form class="dialog library-import" role="dialog" aria-modal="true"><h2>${tr("Перенести {0}", kind==='manga'?tr("мангу"):tr("аниме"))}</h2><p class="hint">${tr("Загрузи JSON, CSV или TXT со своим списком. Можно вставить строки «название — ссылка». Сначала появится предпросмотр; существующие записи останутся.")}</p><div class="form"><label>${tr("Файл списка")}<input name="file" type="file" accept=".json,.csv,.txt,application/json,text/csv,text/plain"></label><label>${tr("Или вставь список")}<textarea name="text" rows="5" placeholder="${tr("Название — https://…")}"></textarea></label></div><p class="library-import-help">${kind==='anime'?tr("Подготовленный файл из профиля YummyAnime содержит 84 тайтла, включая статусы и избранное. Номер просмотренной серии в публичном списке не указан."):tr("Публичный профиль ReManga не показывает закладки. Для переноса нужен экспорт списка или скопированные ссылки со страницы собственных закладок.")}</p><button class="btn" type="button" data-preview>${tr("Проверить список")}</button><p class="auth-error" data-error role="alert"></p><div class="library-preview" data-preview-list></div><div class="dialog-actions"><button class="btn" type="button" data-cancel>${tr("Отмена")}</button><button class="btn primary" type="submit" disabled data-apply>${tr("Перенести тайтлы")}</button></div></form></div>`;
      const form=modal.querySelector('form'),file=form.querySelector('[name="file"]'),text=form.querySelector('[name="text"]'),error=form.querySelector('[data-error]'),preview=form.querySelector('[data-preview-list]'),apply=form.querySelector('[data-apply]');
      if(kind==='manga'){
        const help=form.querySelector('.library-import-help');help.textContent=tr("Нет экспорта на ReManga? Сохрани кнопку выгрузки в браузере и запусти её на странице своих закладок. ");
        const link=document.createElement('a');link.href='remanga-export.html';link.target='_blank';link.rel='noopener noreferrer';link.textContent=tr("Открыть инструкцию и кнопку выгрузки ↗");help.append(link);
      }
      function prepare(value){if(lease!==generation||!isUnlocked())return;try{pending=model.parseFile(value,kind);const merged=model.merge(getItems(),pending);error.textContent='';preview.innerHTML=`<p>${tr("Найдено: ")}<strong>${pending.length}</strong>${tr(" · Новых: ")}<strong>${merged.added}</strong>${tr(" · Дополнено: ")}<strong>${merged.updated}</strong>${tr(" · Без изменений: {0}", merged.skipped)}</p><p>${tr("Повторный импорт исправляет «title image», дополняет неизвестный статус, избранное и пустые заметки. Твой прогресс и выбранный статус сохраняются.")}</p><ul>${pending.slice(0,50).map(x=>`<li>${esc(x.Title)} <span>${esc(label(x.Status))}${x.Favorite?' · ★':''}</span></li>`).join('')}</ul>${pending.length>50?`<p>${tr("Показаны первые 50 тайтлов. Будет перенесён весь список.")}</p>`:''}`;apply.disabled=merged.added===0&&merged.updated===0;}
        catch(caught){pending=[];preview.innerHTML='';apply.disabled=true;error.textContent=caught.message;}}
      function resetImport(){pending=[];apply.disabled=true;preview.innerHTML='';error.textContent='';}
      file.onchange=async()=>{const revision=++readRevision;resetImport();const selected=file.files[0];if(!selected)return;if(selected.size>4*1024*1024){error.textContent=tr("Размер файла — до 4 МБ.");return;}try{const value=await selected.text();if(revision===readRevision)prepare(value);}catch{if(revision===readRevision)error.textContent=tr("Не удалось прочитать файл.");}};
      form.querySelector('[data-preview]').onclick=()=>{readRevision++;prepare(text.value);};text.oninput=()=>{readRevision++;resetImport();};form.querySelector('[data-cancel]').onclick=close;
      form.onsubmit=async event=>{event.preventDefault();if(lease!==generation||!isUnlocked()||!pending.length||apply.disabled)return;apply.disabled=true;let added=0,updated=0;const ok=await change(items=>{const result=model.merge(items,pending);added=result.added;updated=result.updated;items.splice(0,items.length,...result.items);});if(ok&&lease===generation){close();toast(tr("Добавлено: {0}. Дополнено: {1}.", added, updated));}};
    }
    grid.addEventListener('click',async event=>{
      const action=event.target.closest('[data-library-action]');if(!action||!isUnlocked())return;const item=getItems().find(x=>x.Id===action.dataset.id);if(!item)return;
      const type=action.dataset.libraryAction;if(type==='edit'){edit(item);return;}
      if(type==='delete'&&!confirm(tr("Удалить «{0}» из списка?", item.Title)))return;
      await change(items=>{const i=items.findIndex(x=>x.Id===item.Id);if(i<0)return;if(type==='favorite')items[i]={...items[i],Favorite:!items[i].Favorite};if(type==='delete')items.splice(i,1);if(type==='increment')items[i]=model.advance(items[i]);});
    });
    query.addEventListener('input',render);status.addEventListener('change',render);
    document.querySelector('#librarySummary').addEventListener('click',event=>{const b=event.target.closest('[data-status]');if(b){status.value=status.value===b.dataset.status?'':b.dataset.status;render();}});
    document.querySelector('#libraryFavorite').onclick=()=>{favoriteOnly=!favoriteOnly;render();};
    folderBar.addEventListener('click',event=>{const button=event.target.closest('[data-folder]');if(button){folder=button.dataset.folder;render();}});
    document.querySelector('#libraryAdd').onclick=()=>edit(null);document.querySelector('#libraryImport').onclick=showImport;
    function clear(){generation++;query.value='';status.value='';favoriteOnly=false;folder='';covers?.clear();render();}
    return {render,setKind,clear};
  }return {mount};
})();
