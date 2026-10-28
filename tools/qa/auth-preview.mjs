// In-memory QA server. Never sends mail; its captured codes are synthetic test artifacts.
import {createServer} from '../../server/src/server.mjs';
import {mkdir,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root=fileURLToPath(new URL('../../',import.meta.url)),output=resolve(root,'artifacts/auth/qa');await mkdir(output,{recursive:true});
const server=createServer({token:'qa-preview-admin-token-123456',staticDirectory:resolve(root,'Application'),auth:{}});
server.listen(0,'127.0.0.1',async()=>{const endpoint=`http://127.0.0.1:${server.address().port}`;await writeFile(resolve(output,'server.json'),JSON.stringify({endpoint}));console.log(endpoint);});
