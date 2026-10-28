import { createServer } from '../../server/src/server.mjs';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const root=fileURLToPath(new URL('../../',import.meta.url));
const directory=await mkdtemp(join(tmpdir(),'memboom-windows-sync-'));
const storagePath=join(directory,'encrypted-vault.json');
const server=createServer({token:'qa-integration-token-123456',storagePath});
try {
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  const endpoint=`http://127.0.0.1:${server.address().port}`;
  const dotnet=process.env.MEMBOOM_DOTNET||resolve(root,'.tools/dotnet/dotnet.exe');
  const dll=resolve(root,'clients/windows/Tests/bin/Release/net10.0/MemBoom.CoreChecks.dll');
  const result=await new Promise((resolve,reject)=>{
    const child=spawn(dotnet,[dll,'--sync-server',endpoint],{cwd:root,stdio:'inherit',windowsHide:true});
    child.once('error',reject);child.once('exit',resolve);
  });
  if(result!==0)throw new Error(`Core integration checks failed (${result})`);
  const stored=await readFile(storagePath,'utf8');
  for(const secret of ['qa-network-secret','qa-network-first','qa-network-second','QaSync!123'])
    if(stored.includes(secret))throw new Error('Plaintext secret found in server storage');
  const devices=JSON.parse(stored).devices;
  if(devices.length!==2||!devices.some(d=>d.name==='QA Windows'&&!d.revokedAt)||!devices.some(d=>d.name==='QA Revoke'&&d.revokedAt)||devices.some(d=>!/^[a-f0-9]{64}$/.test(d.tokenHash)||'deviceToken' in d))
    throw new Error('Registered device token must be stored only as a hash on the server');
  console.log('PASS real server storage contains only ciphertext; test server used a temporary port and profile');
} finally {
  server.closeAllConnections();
  await new Promise(resolve=>server.close(resolve));
  await rm(directory,{recursive:true,force:true});
}
