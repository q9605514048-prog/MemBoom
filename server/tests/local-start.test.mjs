import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {ensureLocalConfig} from '../src/local-config.mjs';
test('local launcher creates a stable configuration without mail dependencies',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'memboom-start-'));
 try{const path=join(directory,'.env');assert.equal(ensureLocalConfig(path),true);const content=await readFile(path,'utf8');assert.match(content,/VAULT_TOKEN=[a-f0-9]{64}/);assert.match(content,/PORT=8788/);assert.match(content,/VAULT_HOST=127\.0\.0\.1/);assert.equal(ensureLocalConfig(path),false);assert.equal(await readFile(path,'utf8'),content);}
 finally{await rm(directory,{recursive:true,force:true});}
});
test('local launcher preserves existing server secrets and settings',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'memboom-existing-'));
 try{const path=join(directory,'.env'),content='VAULT_TOKEN=existing-custom-token\nPORT=8790\n';await writeFile(path,content);assert.equal(ensureLocalConfig(path),false);assert.equal(await readFile(path,'utf8'),content);}
 finally{await rm(directory,{recursive:true,force:true});}
});
