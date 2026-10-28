globalThis.MemBoomI18n=(()=>{
  let saved;try{saved=localStorage.getItem('memboom.language.v1');}catch{}
  const language=['ru','en'].includes(saved)?saved:globalThis.navigator?.language?.toLowerCase().startsWith('en')?'en':'ru';
  const messages=globalThis.MemBoomTranslations??{};
  function t(key,...args){const value=language==='en'?(messages[key]??key):key;return value.replace(/\{(\d+)\}/g,(_,n)=>String(args[+n]??''));}
  function select(value){if(!['ru','en'].includes(value))return;try{localStorage.setItem('memboom.language.v1',value);}catch{return;}location.reload();}
  function mount(){
    document.documentElement.lang=language;
    document.querySelectorAll('[data-i18n]').forEach(el=>{const text=el.textContent;el.textContent=(text.match(/^\s*/)?.[0]??'')+t(el.getAttribute('data-i18n'))+(text.match(/\s*$/)?.[0]??'');});
    for(const attr of ['placeholder','title','aria-label','alt'])document.querySelectorAll('[data-i18n-'+attr+']').forEach(el=>el.setAttribute(attr,t(el.getAttribute('data-i18n-'+attr))));
    const picker=document.createElement('select');picker.id='languagePicker';picker.setAttribute('aria-label',language==='en'?'Language':'Язык');
    for(const [value,label] of [['ru','Русский'],['en','English']]){const option=document.createElement('option');option.value=value;option.textContent=label;picker.append(option);}
    picker.value=language;picker.addEventListener('change',()=>select(picker.value));
    picker.style.cssText='position:fixed;right:12px;top:8px;z-index:20;background:#24172f;color:#fff4d4;border:1px solid #ffc85788;border-radius:9px;padding:6px 9px;font:13px system-ui;max-width:130px';document.body.append(picker);
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',mount,{once:true});else mount();
  return {t,language,select};
})();
