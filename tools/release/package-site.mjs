import {mkdir,copyFile,cp} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root=fileURLToPath(new URL('../../',import.meta.url)),output=resolve(root,'artifacts/public-site');
await mkdir(output,{recursive:true});
for(const name of ['index.html','remanga-export.html'])await copyFile(resolve(root,'Application',name),resolve(output,name));
for(const name of ['scripts','styles'])await cp(resolve(root,'Application',name),resolve(output,name),{recursive:true});
console.log('Static website prepared: artifacts/public-site');
