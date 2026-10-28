import {loadEnvFile} from 'node:process';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFile} from 'node:child_process';
import {ensureLocalConfig} from '../../server/src/local-config.mjs';
import {createServer} from '../../server/src/server.mjs';
import {createHomeListener} from './home-listener.mjs';

const root=fileURLToPath(new URL('../../',import.meta.url)),directory=resolve(root,'server');
try{
  const config=resolve(directory,'.env');ensureLocalConfig(config);loadEnvFile(config);
  const token=process.env.VAULT_TOKEN,port=Number(process.env.PORT||8788),host=process.env.VAULT_HOST||'127.0.0.1';
  if(!token||token.length<16||!Number.isInteger(port)||port<1||port>65535)throw Error('Проверьте VAULT_TOKEN и PORT в server/.env.');
  const storage=process.env.VAULT_STORAGE_PATH?resolve(directory,process.env.VAULT_STORAGE_PATH):resolve(directory,'data','vault.json');
  const server=createServer({token,storagePath:storage,auth:{storagePath:resolve(storage,'..','accounts.json'),vaultDirectory:resolve(storage,'..','users')},staticDirectory:resolve(root,'Application'),allowedOrigins:(process.env.AUTH_ALLOWED_ORIGINS||'').split(',').map(s=>s.trim()).filter(Boolean)});
  server.once('error',error=>{console.error(error.code==='EADDRINUSE'?`Порт ${port} уже занят. Закройте прежнее окно сервера MemBoom или измените PORT в server/.env.`:'Не удалось запустить MemBoom. Проверьте настройки и доступ к локальной сети.');process.exitCode=1;});
  server.listen(port,host,()=>{
    const browserHost=['0.0.0.0','::'].includes(host)?'127.0.0.1':host.includes(':')?`[${host}]`:host;
    const url=`http://${browserHost}:${port}/`;
    console.log(`MemBoom: ${url}\nОставьте это окно открытым. Для регистрации используйте этот адрес, а не index.html с диска.`);
    if(process.argv.includes('--open')&&process.platform==='win32')execFile('rundll32.exe',['url.dll,FileProtocolHandler',url],{windowsHide:true},()=>{});
    if(process.env.VAULT_LAN_HOST){
      const lanServer=createHomeListener(server,process.env.VAULT_LAN_HOST);
      lanServer.once('error',()=>console.error('Домашний адрес недоступен. Проверьте подключение ПК к роутеру. Сайт на ПК продолжает работать.'));
      lanServer.listen(port,process.env.VAULT_LAN_HOST,()=>console.log(`MemBoom для телефона: http://${process.env.VAULT_LAN_HOST}:${port}/`));
    }
  });
}catch(error){console.error(error.message?.startsWith('Проверьте')?error.message:'Не удалось прочитать настройки MemBoom. Существующие данные не удалены.');process.exitCode=1;}
