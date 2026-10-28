import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root=fileURLToPath(new URL('../../',import.meta.url));
const esc=s=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const sections=[];
for(const lang of ['en','ru']){
 const text=await readFile(resolve(root,`legal/terms.${lang}.txt`),'utf8');
 await writeFile(resolve(root,`clients/android/app/src/main/assets/terms-${lang}.txt`),text);
 sections.push(`<article id="${lang}" lang="${lang}">${text.trim().split(/\r?\n\r?\n/).map((p,i)=>i===0?`<h1>${esc(p)}</h1>`:i===1?`<p class="version">${esc(p)}</p>`:`<section><h2>${esc(p.split('\n')[0])}</h2><p>${esc(p.split('\n').slice(1).join('\n'))}</p></section>`).join('')}</article>`);
}
await writeFile(resolve(root,'Application/terms.html'),`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>MemBoom — Terms of Use / Пользовательское соглашение</title><style>body{margin:0;background:#110b1b;color:#f9eefa;font:16px/1.7 system-ui}main{max-width:860px;margin:auto;padding:32px 22px}a{color:#ffc857}nav{display:flex;gap:20px;flex-wrap:wrap}h1{font-size:28px}h2{font-size:20px;margin-top:30px}.version{color:#c7aeca}article{scroll-margin-top:20px}article+article{margin-top:70px;border-top:1px solid #5c2f68;padding-top:32px}p{white-space:pre-line}</style></head><body><main><nav><a href="index.html">← MemBoom</a><a href="#en">English</a><a href="#ru">Русский</a></nav>${sections.join('')}</main></body></html>`);
console.log('Built bilingual website terms and offline Android terms.');
