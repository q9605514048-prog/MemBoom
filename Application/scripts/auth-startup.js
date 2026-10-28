globalThis.MemBoomAuthStartup=(()=>{
  const key='memboom.auth.identity.v1';
  function read(){try{const value=JSON.parse(localStorage.getItem(key));return value&&typeof value.login==='string'&&/^[A-Za-z0-9_.-]{3,32}$/.test(value.login)&&typeof value.endpoint==='string'?{login:value.login,endpoint:value.endpoint}:null;}catch{return null;}}
  function remember(session){try{localStorage.setItem(key,JSON.stringify({login:session.user.login,endpoint:session.endpoint}));localStorage.removeItem('memboom.local-mode.v1');}catch{}}
  function useLocal(){try{localStorage.setItem('memboom.local-mode.v1','true');}catch{}}
  function isLocal(){try{return localStorage.getItem('memboom.local-mode.v1')==='true';}catch{return false;}}
  return {read,remember,useLocal,isLocal};
})();
