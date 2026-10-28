globalThis.MemBoomAuth=(()=>{
const tr=(key,...args)=>globalThis.MemBoomI18n?.t(key,...args)??key.replace(/\{(\d+)\}/g,(_,n)=>String(args[+n]??''));

  const messages={invalid_credentials:tr("Неверный логин или пароль."),login_unavailable:tr("Этот логин уже используется."),too_many_attempts:tr("Слишком много попыток. Подождите перед повтором."),invalid_registration:tr("Проверьте логин и пароль (минимум 4 символа без пробелов)."),unauthorized:tr("Сессия завершена. Войдите снова."),origin_not_allowed:tr("Откройте сайт по адресу сервера MemBoom или настройте разрешённый адрес сайта.")};
  class Client{
    constructor(endpoint){
      const url=new URL(endpoint);
      if(url.username||url.password||url.search||url.hash||url.pathname!=='/'||!(url.protocol==='https:'||(url.protocol==='http:'&&['localhost','127.0.0.1','[::1]'].includes(url.hostname))))throw Error(tr("Для внешнего сервера нужен HTTPS; укажите адрес без пути."));
      this.endpoint=url.origin;
    }
    async request(path,body,token){
      if(globalThis.location?.protocol==='file:')throw Error(tr("Запустите «Запустить сервер.bat» и откройте сайт по адресу http://127.0.0.1:8788/. Регистрация из локального файла недоступна."));
      const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),20000);
      try{
        let response;
        try{response=await fetch(this.endpoint+'/api/auth/'+path,{method:'POST',headers:{'content-type':'application/json',...(token?{authorization:'Bearer '+token}:{})},body:JSON.stringify(body),signal:controller.signal,redirect:'error',cache:'no-store'});}
        catch(error){if(error.name==='AbortError')throw error;throw Error(tr("Не удалось подключиться к серверу {0}. Запустите «Запустить сервер.bat» и откройте сайт по адресу сервера.", this.endpoint));}
        if(response.status===204)return null;
        const text=await response.text();if(text.length>32768)throw Error(tr("Некорректный ответ сервера."));
        let json;try{json=JSON.parse(text);}catch{throw Error(tr("По указанному адресу нет API MemBoom. Откройте http://127.0.0.1:8788/."));}
        if(!response.ok)throw Error(messages[json.error]||tr("Не удалось выполнить вход."));return json;
      }catch(error){if(error.name==='AbortError')throw Error(tr("Сервер не ответил вовремя. Проверьте, что MemBoom запущен."));throw error;}
      finally{clearTimeout(timeout);}
    }
    login(login,password){return this.request('login',{login,password});}
    register(login,password){return this.request('register',{login,password});}
    logout(token){return this.request('logout',{},token);}
  }
  return {Client};
})();
