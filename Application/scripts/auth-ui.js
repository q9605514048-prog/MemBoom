globalThis.MemBoomAuthUI=(()=>{
  const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function mount({onSession=()=>{},onLogout=()=>{}}={}){
    const preferences=globalThis.MemBoomAuthStartup;
    let saved=preferences?.read(),session=null,overlay=null,busy=false,epoch=0;
    let endpoint=saved?.endpoint??(['http:','https:'].includes(location.protocol)?location.origin:'http://127.0.0.1:8788');
    let mode=saved?'login':'welcome';
    function update(){
      ['#remoteAccountBtn','#remoteAccountLockBtn'].forEach(id=>{const b=document.querySelector(id);if(b)b.textContent=session?`Аккаунт: ${session.user.login}`:'Вход / Регистрация';});
      const label=document.querySelector('#remoteAccountLabel');if(label)label.textContent=session?`Логин: ${session.user.login}`:'Локальное хранилище';
    }
    function clear(revoke=false){const old=session;epoch++;session=null;busy=false;overlay?.remove();overlay=null;update();onLogout();if(revoke&&old)new MemBoomAuth.Client(old.endpoint).logout(old.token).catch(()=>{});}
    function draw(){
      overlay?.remove();overlay=document.createElement('div');overlay.className='overlay remote-auth';overlay.id='remoteAuthOverlay';
      const heading=session?'Аккаунт MemBoom':mode==='welcome'?'Добро пожаловать в MemBoom':mode==='register'?'Создать аккаунт':saved?'С возвращением!':'Войти в MemBoom';
      const profile=session?`<p>Логин: ${esc(session.user.login)}</p><div class="dialog-actions"><button class="btn" type="button" data-close>Закрыть</button><button class="btn danger" type="submit">Выйти из аккаунта</button></div>`:'';
      const welcome=mode==='welcome'?'<p class="hint">Создайте аккаунт или войдите, если он у вас уже есть.</p><div class="auth-choice"><button class="btn primary" type="button" data-mode="register">Создать аккаунт</button><button class="btn" type="button" data-mode="login">Войти</button></div>':`<p class="hint">${mode==='register'?'Придумайте логин и пароль.':saved?'Введите пароль, чтобы продолжить.':'Введите логин и пароль.'}</p><div class="form"><label>Адрес сервера MemBoom<input name="endpoint" type="url" value="${esc(endpoint)}" required></label><label>Логин<input name="login" value="${esc(mode==='login'?saved?.login??'':'')}" pattern="[A-Za-z0-9_.-]{3,32}" autocomplete="username" required></label><label>Пароль<input name="password" type="password" minlength="4" maxlength="256" pattern="[!-~]{4,256}" autocomplete="${mode==='register'?'new-password':'current-password'}" required></label>${mode==='register'?'<label>Повторите пароль<input name="repeat" type="password" autocomplete="new-password" required></label>':''}</div><div class="dialog-actions"><button class="btn" type="button" data-mode="welcome">Назад</button><button class="btn primary" type="submit">${mode==='register'?'Создать аккаунт':'Войти'}</button></div>`;
      const launchHelp=location.protocol==='file:'?'<p class="auth-error">Сайт открыт с диска. Запустите «Запустить сервер.bat» в папке MemBoom, затем <a href="http://127.0.0.1:8788/" target="_blank" rel="noopener noreferrer">откройте MemBoom по HTTP</a>. Старые локальные записи остаются в этом браузере.</p>':'';
      overlay.innerHTML=`<form class="dialog auth-dialog" role="dialog" aria-modal="true" aria-labelledby="authHeading"><h2 id="authHeading">${heading}</h2>${launchHelp}${session?profile:welcome}<p class="auth-error" data-error role="alert"></p></form>`;
      document.body.append(overlay);const form=overlay.querySelector('form');
      if(!session&&mode==='welcome'){
        const local=document.createElement('button');local.type='button';local.className='btn auth-local';local.textContent='Попробовать без сервера';
        local.addEventListener('click',()=>{if(busy)return;preferences?.useLocal();epoch++;overlay?.remove();overlay=null;});
        form.append(local);
        const hint=document.createElement('p');hint.className='hint';hint.textContent='Аккаунты и заметки сохраняются на вашем устройстве. Для синхронизации можно подключить свой сервер.';form.append(hint);
      }
      form.querySelector('[data-close]')?.addEventListener('click',()=>{if(!busy){epoch++;overlay.remove();overlay=null;}});
      form.querySelectorAll('[data-mode]').forEach(b=>b.addEventListener('click',()=>{if(!busy){endpoint=form.elements.endpoint?.value??endpoint;mode=b.dataset.mode;draw();}}));
      form.onsubmit=async event=>{
        event.preventDefault();if(busy||(!session&&mode==='welcome'))return;
        const generation=epoch,action=mode,old=session,data=new FormData(form),error=form.querySelector('[data-error]');
        if(action==='register'&&data.get('password')!==data.get('repeat')){error.textContent='Пароли не совпадают.';return;}
        busy=true;form.querySelectorAll('button').forEach(b=>b.disabled=true);
        try{
          if(old){await new MemBoomAuth.Client(old.endpoint).logout(old.token);if(generation===epoch){clear();saved=preferences?.read();mode=saved?'login':'welcome';draw();}return;}
          endpoint=String(data.get('endpoint')).trim();const client=new MemBoomAuth.Client(endpoint),password=String(data.get('password'));
          const result=action==='register'?await client.register(String(data.get('login')),password):await client.login(String(data.get('login')),password);
          if(generation!==epoch)return;
          if(!result.user?.login||typeof result.token!=='string')throw Error('Некорректный ответ сервера.');
          session={...result,endpoint};preferences?.remember(session);saved=preferences?.read();
          // The password exists only during this callback, never in browser preferences.
          await onSession(session,password);
          if(generation!==epoch)return;overlay?.remove();overlay=null;update();
        }catch(caught){if(generation===epoch)error.textContent=caught.message??'Не удалось выполнить вход.';}
        finally{if(generation===epoch){busy=false;form.querySelectorAll('button').forEach(b=>b.disabled=false);}}
      };
      (form.querySelector(saved&&mode==='login'?'[name="password"]':'input')??form.querySelector('button'))?.focus();
    }
    function open(){if(!busy){mode=saved?'login':'welcome';draw();}}
    function attachLockForm(){const form=document.querySelector('#lockForm');if(!form)return;let b=document.querySelector('#remoteAccountLockBtn');if(!b){b=document.createElement('button');b.type='button';b.id='remoteAccountLockBtn';b.className='btn auth-lock-launch';form.append(b);}b.onclick=open;update();}
    document.querySelector('#remoteAccountBtn')?.addEventListener('click',open);attachLockForm();update();
    return {get session(){return session;},clear,open,start(){if(!preferences?.isLocal())open();},attachLockForm};
  }
  return {mount};
})();
