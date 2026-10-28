/* Plain script: works both over HTTP and when index.html is opened from disk. */
globalThis.MemBoomNotes = (() => {
  function link(value){
    if(!value||typeof value.Name!=='string'||!value.Name.trim()||value.Name.length>160||typeof value.Url!=='string'||value.Url.length>4096)throw Error('Укажи название файла (до 160 символов) и ссылку.');
    let url;try{url=new URL(value.Url.trim());}catch{throw Error('Проверь ссылку на файл.');}
    const host=url.hostname.toLowerCase();
    const provider=['drive.google.com','docs.google.com'].includes(host)?'Google Диск':['disk.yandex.ru','disk.yandex.com','yadi.sk'].includes(host)?'Яндекс Диск':null;
    if(url.protocol!=='https:'||url.username||url.password||url.port||!provider)throw Error('Нужна HTTPS-ссылка на Google Диск или Яндекс Диск.');
    return {Name:value.Name.trim(),Url:url.href,Provider:provider};
  }
  function links(values){
    if(values===undefined)return [];
    if(!Array.isArray(values)||values.length>20)throw Error('К заметке можно добавить до 20 ссылок на файлы.');
    const rows=values.map(link),seen=new Set();for(const row of rows){if(seen.has(row.Url))throw Error('Эта ссылка на файл уже добавлена.');seen.add(row.Url);}return rows;
  }
  function validate(notes) {
    if (!Array.isArray(notes)) throw Error('Invalid notes');
    const ids = new Set();
    for (const n of notes) {
      if (!n || typeof n.Id !== 'string' || !n.Id.trim() || ids.has(n.Id) || typeof n.Title !== 'string' || !n.Title.trim() || n.Title.length > 200 || typeof n.Content !== 'string' || n.Content.length > 500000 || typeof n.Pinned !== 'boolean' || typeof n.UpdatedAt !== 'string' || !Number.isFinite(Date.parse(n.UpdatedAt))) throw Error('Invalid note');
      links(n.Links);ids.add(n.Id);
    }
    return notes;
  }
  function parse(value) {
    const categories = Array.isArray(value) ? value : value?.categories;
    if (!Array.isArray(categories) || categories.some(c => !c || typeof c.name !== 'string' || !Array.isArray(c.accounts))) throw Error('Invalid categories');
    let library=Array.isArray(value)?[]:value.library??[];
    if(!Array.isArray(library))throw Error('Invalid library');
    if(globalThis.MemBoomLibrary)library=globalThis.MemBoomLibrary.migrate(library);
    return { categories, notes: validate(Array.isArray(value) ? [] : value.notes ?? []),library };
  }
  function merge(local, incoming) {
    validate(local); validate(incoming);
    const result = local.map(n => ({...n}));
    for (const n of incoming) {
      if (result.some(x => x.Title === n.Title && x.Content === n.Content && x.Pinned === n.Pinned && JSON.stringify(links(x.Links))===JSON.stringify(links(n.Links)))) continue;
      result.push({...n, Id: result.some(x => x.Id === n.Id) ? crypto.randomUUID() : n.Id});
    }
    return result;
  }
  function search(notes, query) {
    const q = query.trim().toLocaleLowerCase('ru');
    return notes.filter(n => `${n.Title}\n${n.Content}\n${(n.Links??[]).map(x=>x.Name+' '+x.Url).join('\n')}`.toLocaleLowerCase('ru').includes(q)).sort((a,b) => Number(b.Pinned)-Number(a.Pinned) || Date.parse(b.UpdatedAt)-Date.parse(a.UpdatedAt));
  }
  return {link,links,validate,parse,merge,search};
})();
