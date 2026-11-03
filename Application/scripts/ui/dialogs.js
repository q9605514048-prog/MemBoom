/* Shared keyboard behavior for the existing dialog host; no dependency/runtime. */
globalThis.MemBoomDialogs=(()=>{
 const tr=(key,...args)=>globalThis.MemBoomI18n?.t(key,...args)??key.replace(/\{(\d+)\}/g,(_,n)=>String(args[+n]??''));
 let pending=null,previous=null,lastDialog=null;
 function cancel(){pending?.(null);pending=null;}
 function password({title,description,repeat=false}){
  cancel();const host=document.querySelector('#modal');
  return new Promise(resolve=>{
   pending=resolve;
   host.innerHTML=`<div class="overlay"><form class="dialog" role="dialog" aria-modal="true" aria-labelledby="passwordDialogTitle"><span class="eyebrow">MEMBOOM · BACKUP</span><h2 id="passwordDialogTitle"></h2><p class="hint" data-description></p><div class="form"><label>${tr('Пароль резервной копии')}<input name="password" type="password" minlength="4" pattern="[!-~]{4,}" required autocomplete="${repeat?'new-password':'current-password'}"></label>${repeat?`<label>${tr('Повторите пароль')}<input name="repeat" type="password" required autocomplete="new-password"></label>`:''}</div><p class="error-message" role="alert" data-error></p><div class="dialog-actions"><button type="button" class="btn" data-cancel>${tr('Отмена')}</button><button class="btn primary">${tr('Продолжить')}</button></div></form></div>`;
   const form=host.querySelector('form');form.querySelector('h2').textContent=title;form.querySelector('[data-description]').textContent=description;
   const close=value=>{pending=null;host.innerHTML='';resolve(value);};form.querySelector('[data-cancel]').onclick=()=>close(null);
   form.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();close(null);}});
   form.onsubmit=e=>{e.preventDefault();const value=form.elements.password.value;if(repeat&&value!==form.elements.repeat.value){form.querySelector('[data-error]').textContent=tr('Пароли не совпадают.');return;}close(value);};form.elements.password.focus();
  });
 }
 function init(){
  const host=document.querySelector('#modal');if(!host||!globalThis.MutationObserver)return;
  new MutationObserver(()=>{
   const dialog=host.querySelector('.dialog');
   if(dialog&&!lastDialog){previous=document.activeElement;dialog.setAttribute('role','dialog');dialog.setAttribute('aria-modal','true');}
   if(!dialog&&lastDialog){cancel();previous?.isConnected&&previous.focus();previous=null;}
   lastDialog=dialog;
  }).observe(host,{childList:true,subtree:false});
  const inertBackground=()=>{
   const blocked=Boolean(document.querySelector('#remoteAuthOverlay, #lockScreen, #modal .overlay'));
   document.querySelectorAll('.app,.sidebar,.mobile-nav').forEach(el=>{el.inert=blocked;});
   const picker=document.querySelector('#languagePicker'),slot=document.querySelector('#languageSlot');
   if(picker){const destination=blocked||innerWidth<=680||!slot?document.body:slot;if(picker.parentElement!==destination)destination.append(picker);picker.classList.toggle('outside-shell',blocked);}
  };
  new MutationObserver(inertBackground).observe(document.body,{childList:true,subtree:true});inertBackground();
  window.addEventListener('resize',inertBackground);
  document.addEventListener('keydown',e=>{
   if(e.defaultPrevented)return;
   const overlay=document.querySelector('#remoteAuthOverlay')??document.querySelector('#modal .overlay')??document.querySelector('#lockScreen');
   if(!overlay)return;const nodes=[...overlay.querySelectorAll('button:not(:disabled),a[href],input:not(:disabled),textarea,select,[tabindex="0"]')].filter(x=>x.getClientRects().length);
   if(e.key==='Tab'&&nodes.length){const first=nodes[0],last=nodes[nodes.length-1];if(e.shiftKey&&(document.activeElement===first||!overlay.contains(document.activeElement))){e.preventDefault();last.focus();}else if(!e.shiftKey&&(document.activeElement===last||!overlay.contains(document.activeElement))){e.preventDefault();first.focus();}}
   if(e.key==='Escape'&&overlay.closest('#modal')){const cancelButton=overlay.querySelector('[data-cancel]');if(cancelButton){e.preventDefault();cancelButton.click();}}
  });
 }
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
 return {password,cancel};
})();
