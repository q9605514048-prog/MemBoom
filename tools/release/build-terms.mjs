import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root=fileURLToPath(new URL('../../',import.meta.url));
const esc=s=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const sections=[];
for(const lang of ['en','ru']){
 const text=await readFile(resolve(root,`legal/terms.${lang}.txt`),'utf8');
 if(!process.argv.includes('--web-only'))await writeFile(resolve(root,`clients/android/app/src/main/assets/terms-${lang}.txt`),text);
 const paragraphs=text.trim().split(/\r?\n\r?\n/);
 const toc=paragraphs.slice(2).map((p,i)=>`<li><a href="#terms-${lang}-${i+1}">${esc(p.split(/\r?\n/)[0])}</a></li>`).join('');
 sections.push(`<article id="${lang}" lang="${lang}" aria-labelledby="terms-${lang}-title"><header><p class="legal-language">${lang==='en'?'ENGLISH':'РУССКИЙ'}</p><h1 id="terms-${lang}-title">${esc(paragraphs[0])}</h1><p class="version">${esc(paragraphs[1])}</p></header><details class="legal-contents"><summary>${lang==='en'?'Contents':'Содержание'}</summary><ol>${toc}</ol></details>${paragraphs.slice(2).map((p,i)=>{const lines=p.split(/\r?\n/);return `<section id="terms-${lang}-${i+1}"><h2>${esc(lines[0])}</h2><p>${esc(lines.slice(1).join('\n'))}</p></section>`;}).join('')}</article>`);
}
await writeFile(resolve(root,'Application/terms.html'),`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>MemBoom — Terms of Use / Пользовательское соглашение</title><style>
:root{color-scheme:dark;--color-night:#100D16;--color-panel:#19131F;--color-panel-strong:#231A2B;--color-magic-gold:#E9BC6A;--color-ink:#F7EDF0;--color-muted:#BAA9BA;--color-line:rgba(255,255,255,.09);font-family:"Segoe UI",Arial,sans-serif;background:var(--color-night);color:var(--color-ink)}
*{box-sizing:border-box}html{scroll-padding-top:88px}body{margin:0;font-size:16px;line-height:1.8}main{max-width:880px;margin:auto;padding:0 28px 64px}a{color:var(--color-magic-gold);text-underline-offset:4px}a:hover{text-decoration-thickness:2px}
nav{position:sticky;top:0;z-index:1;display:flex;align-items:center;gap:8px 24px;flex-wrap:wrap;padding:16px 0;margin-bottom:40px;background:var(--color-night);border-bottom:1px solid var(--color-line)}nav a{display:inline-flex;align-items:center;min-height:44px;font-size:14px;text-decoration:none}nav a:first-child{margin-right:auto;color:var(--color-ink);font-size:18px;font-weight:700}.legal-mark{color:var(--color-magic-gold);margin-right:12px;font-size:24px}
.legal-language{margin:0 0 12px;font-size:12px;letter-spacing:.16em;color:var(--color-magic-gold)}h1{margin:0 0 12px;font-size:clamp(28px,4vw,40px);line-height:1.2;letter-spacing:-.03em;text-wrap:balance}h2{font-size:20px;line-height:1.4;margin:0 0 12px;letter-spacing:-.015em}.version{color:var(--color-muted);font-size:14px;margin:0 0 28px}
article{scroll-margin-top:88px}article+article{margin-top:64px;padding-top:40px;border-top:1px solid var(--color-line)}section{padding:24px 0;border-bottom:1px solid var(--color-line);scroll-margin-top:88px}section:target{border-bottom-color:var(--color-magic-gold)}p{white-space:pre-line;overflow-wrap:anywhere;margin:0}section p{max-width:76ch}
.legal-contents{border:1px solid var(--color-line);border-radius:16px;background:var(--color-panel);padding:8px 20px;margin:24px 0 8px}summary{min-height:44px;padding:8px 0;color:var(--color-magic-gold);cursor:pointer}ol{padding:0;margin:8px 0 12px;list-style:none}.legal-contents a{display:flex;align-items:center;min-height:44px;padding:8px 0;font-size:14px;line-height:1.6}
a:focus-visible,summary:focus-visible{outline:2px solid var(--color-magic-gold);outline-offset:4px}
@media(max-width:600px){main{padding:0 20px 40px}nav{margin-bottom:28px;gap:8px 20px}body{font-size:15px;line-height:1.8}h2{font-size:18px}section{padding:20px 0}article+article{margin-top:40px;padding-top:28px}}
@media(prefers-reduced-motion:reduce){html{scroll-behavior:auto}*{animation:none;transition:none}}
@media print{:root{color:#111;background:#fff}main{max-width:none;padding:0}nav,.legal-contents{display:none}article+article{break-before:page}section{break-inside:avoid;border-color:#ccc}h1,h2,.version,.legal-language{color:#111}}
</style></head><body><main><nav aria-label="Document navigation / Навигация по документу"><a href="index.html"><span class="legal-mark" aria-hidden="true">✦</span>MemBoom</a><a href="#en" lang="en" hreflang="en">English</a><a href="#ru" lang="ru" hreflang="ru">Русский</a></nav>${sections.join('')}</main></body></html>`);
console.log(process.argv.includes('--web-only')?'Built bilingual website terms only.':'Built bilingual website terms and offline Android terms.');
