import {createServer} from '../../server/src/server.mjs';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root=fileURLToPath(new URL('../../',import.meta.url));
const server=createServer({token:'qa-auth-server-123456',auth:{}});
try{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const endpoint=`http://127.0.0.1:${server.address().port}`;
  const send=async(path,body)=>{const response=await fetch(endpoint+'/api/auth/'+path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)});if(!response.ok)throw Error('QA setup failed');return response.json();};
  const login='qa_native',password='QaNative!123';
  const data={endpoint,login,password};
  const result=await new Promise((resolveResult,reject)=>{const child=spawn(process.env.MEMBOOM_DOTNET||resolve(root,'.tools/dotnet/dotnet.exe'),[resolve(root,'clients/windows/Tests/bin/Release/net10.0/MemBoom.CoreChecks.dll'),'--auth-integration'],{cwd:root,stdio:['pipe','inherit','inherit'],windowsHide:true});child.once('error',reject);child.once('exit',resolveResult);child.stdin.end(JSON.stringify(data));});
  if(result!==0)throw Error('Native account integration failed');console.log('PASS remembered native login and registration against real local API');
}finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
