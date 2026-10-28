globalThis.MemBoomNoteLinks=(()=>{
const tr=(key,...args)=>globalThis.MemBoomI18n?.t(key,...args)??key.replace(/\{(\d+)\}/g,(_,n)=>String(args[+n]??''));

  const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function render(values){
    const rows=MemBoomNotes.links(values);if(!rows.length)return '';
    return `<div class="note-file-links" aria-label="${tr("Файлы заметки")}">${rows.map(x=>`<a class="note-file-link" href="${esc(x.Url)}" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer"><span class="note-file-icon" aria-hidden="true">↗</span><span><strong>${esc(x.Name)}</strong><small>${esc(x.Provider)}</small></span><span class="note-file-open">${tr("Открыть ↗")}</span></a>`).join('')}</div>`;
  }
  function createEditor(form,initial=[]){
    let rows=initial.map(x=>({Name:x.Name,Url:x.Url}));
    const section=document.createElement('section');section.className='note-files-editor';
    section.innerHTML=`<div class="note-files-heading"><h3>${tr("Ссылки на файлы")}</h3><button class="btn small" type="button" data-add-file>${tr("＋ Ссылка")}</button></div><p class="hint">${tr("Вставь ссылку Google Диска или Яндекс Диска. Файл откроется в новой вкладке.")}</p><div data-file-rows></div><p data-file-error class="auth-error" role="alert"></p>`;
    form.querySelector('.form').append(section);
    const list=section.querySelector('[data-file-rows]'),error=section.querySelector('[data-file-error]'),add=section.querySelector('[data-add-file]');
    function capture(){rows=Array.from(list.querySelectorAll('[data-file-row]')).map(row=>({Name:row.querySelector('[data-file-name]').value,Url:row.querySelector('[data-file-url]').value}));return rows;}
    function paint(){
      list.innerHTML=rows.map((x,i)=>`<div class="note-file-row" data-file-row><label>${tr("Название файла")}<input data-file-name maxlength="160" value="${esc(x.Name)}" placeholder="${tr("Например, чертёж")}"></label><label>${tr("Ссылка на файл")}<input data-file-url type="url" maxlength="4096" value="${esc(x.Url)}" placeholder="https://drive.google.com/…"></label><button class="copy" type="button" data-remove-file="${i}" aria-label="${tr("Убрать ссылку {0}", esc(x.Name||String(i+1)))}">×</button></div>`).join('');add.disabled=rows.length>=20;
    }
    add.onclick=()=>{capture();if(rows.length>=20)return;rows.push({Name:'',Url:''});paint();list.querySelector('[data-file-row]:last-child [data-file-name]')?.focus();};
    list.addEventListener('click',event=>{const button=event.target.closest('[data-remove-file]');if(!button)return;capture();rows.splice(Number(button.dataset.removeFile),1);error.textContent='';paint();});
    function read(){
      error.textContent='';try{return MemBoomNotes.links(capture().filter(x=>x.Name.trim()||x.Url.trim()));}catch(e){error.textContent=e.message;throw e;}
    }
    function dirty(){return JSON.stringify(capture().filter(x=>x.Name.trim()||x.Url.trim()))!==JSON.stringify(initial.map(x=>({Name:x.Name,Url:x.Url})));}
    paint();return {read,dirty};
  }
  return {render,createEditor};
})();
