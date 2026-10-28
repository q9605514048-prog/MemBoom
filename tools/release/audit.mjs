import {execFileSync} from 'node:child_process';
import {readFileSync,statSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
const files=[...new Set(execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z'],{encoding:'utf8'}).split('\0').filter(Boolean))];
if(!files.length)throw Error('No publication files');
const problems=[];
const configPath=resolve('server/.env');
const serverSecret=existsSync(configPath)?readFileSync(configPath,'utf8').match(/^VAULT_TOKEN=(.+)$/m)?.[1]?.trim():null;
for(const path of files){
 if(!path.includes('/')&&!['.gitattributes','.gitignore','README.md','SECURITY.md','LICENSE','LICENSE.md','NOTICE','CHANGELOG.md','Запустить сервер.bat'].includes(path))problems.push(path+': unexpected root file; review before publishing');
 if(/(^|\/)(?:node_modules|artifacts|foto|\.gradle-home|\.tools|data)(\/|$)|(^|\/)\.env(?:$|\.(?!example$))|\.(?:jks|keystore|pfx|p12|pem|key)$/i.test(path))problems.push(path+': excluded file type');
 if(statSync(path).size>20*1024*1024)problems.push(path+': unexpectedly large source file');
 if(/\.(?:png|jpg|ico|jar)$/i.test(path))continue;
 const text=readFileSync(path,'utf8');
 if(/gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/.test(text))problems.push(path+': credential pattern');
 if(serverSecret&&serverSecret.length>=16&&text.includes(serverSecret))problems.push(path+': local server secret');
 if(/C:[\\/]+Users[\\/]+[^\\/\s"']+[\\/]/i.test(text))problems.push(path+': personal absolute path');
}
if(problems.length){console.error(problems.join('\n'));process.exit(1);}
console.log(`Publication audit passed: ${files.length} source files; no local data, signing keys or detected credentials.`);
