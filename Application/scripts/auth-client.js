globalThis.MemBoomAuth=(()=>{
  const messages={invalid_credentials:'Неверный логин или пароль.',login_unavailable:'Этот логин уже используется.',too_many_attempts:'Слишком много попыток. Подождите перед повтором.',invalid_registration:'Проверьте логин и пароль (минимум 4 символа без пробелов).',unauthorized:'Сессия завершена. Войдите снова.',origin_not_allowed:'Откройте сайт по адресу сервера MemBoom или настройте разрешённый адрес сайта.'};
  class Client{
    constructor(endpoint){
      const url=new URL(endpoint);
      if(url.username||url.password||url.search||url.hash||url.pathname!=='/'||!(url.protocol==='https:'||(url.protocol==='http:'&&['localhost','127.0.0.1','[::1]'].includes(url.hostname))))throw Error('Для внешнего сервера нужен HTTPS; укажите адрес без пути.');
      this.endpoint=url.origin;
    }
    async request(path,body,token){
      if(globalThis.location?.protocol==='file:')throw Error('Запустите «Запустить сервер.bat» и откройте сайт по адресу http://127.0.0.1:8788/. Регистрация из локального файла недоступна.');
      const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),20000);
      try{
        let response;
        try{response=await fetch(this.endpoint+'/api/auth/'+path,{method:'POST',headers:{'content-type':'application/json',...(token?{authorization:'Bearer '+token}:{})},body:JSON.stringify(body),signal:controller.signal,redirect:'error',cache:'no-store'});}
        catch(error){if(error.name==='AbortError')throw error;throw Error(`Не удалось подключиться к серверу ${this.endpoint}. Запустите «Запустить сервер.bat» и откройте сайт по адресу сервера.`);}
        if(response.status===204)return null;
        const text=await response.text();if(text.length>32768)throw Error('Некорректный ответ сервера.');
        let json;try{json=JSON.parse(text);}catch{throw Error('По указанному адресу нет API MemBoom. Откройте http://127.0.0.1:8788/.');}
        if(!response.ok)throw Error(messages[json.error]||'Не удалось выполнить вход.');return json;
      }catch(error){if(error.name==='AbortError')throw Error('Сервер не ответил вовремя. Проверьте, что MemBoom запущен.');throw error;}
      finally{clearTimeout(timeout);}
    }
    login(login,password){return this.request('login',{login,password});}
    register(login,password){return this.request('register',{login,password});}
    logout(token){return this.request('logout',{},token);}
  }
  return {Client};
})();
