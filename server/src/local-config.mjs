import {existsSync,mkdirSync,writeFileSync} from 'node:fs';
import {dirname} from 'node:path';
import {randomBytes} from 'node:crypto';

export function ensureLocalConfig(path){
  if(existsSync(path))return false;
  mkdirSync(dirname(path),{recursive:true});
  try{writeFileSync(path,`VAULT_TOKEN=${randomBytes(32).toString('hex')}\nPORT=8788\nVAULT_HOST=127.0.0.1\n`,{flag:'wx',mode:0o600});return true;}
  catch(error){if(error.code==='EEXIST')return false;throw error;}
}
