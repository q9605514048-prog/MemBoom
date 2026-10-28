import { readFile } from 'node:fs/promises';
import { webcrypto } from 'node:crypto';
import vm from 'node:vm';

const html = await readFile(new URL('../../index.html', import.meta.url), 'utf8');
const scriptStart=html.indexOf('<script>')+8;
const script = html.slice(scriptStart, html.indexOf('</script>',scriptStart));
const notesSource=await readFile(new URL('../../scripts/notes-model.js',import.meta.url),'utf8');
const noteLinksSource=await readFile(new URL('../../scripts/note-links.js',import.meta.url),'utf8');
const librarySource=await readFile(new URL('../../scripts/library/model.js',import.meta.url),'utf8');

function element() {
  return { value: '', innerHTML: '', textContent: '', listeners: {}, attributes: {}, classList: { add() {}, remove() {}, toggle() {} },
    addEventListener(name, handler) { this.listeners[name] = handler; },
    setAttribute(name, value) { this.attributes[name] = String(value); },
    getAttribute(name) { return this.attributes[name] ?? null; },
    focus() {}, remove() {}, setCustomValidity(message) { this.validationMessage = message; }, reportValidity() {} };
}

export async function openVault(storage = new Map(),environment = {}) {
  let library;
  const elements = new Map();
  const get = selector => { if (!elements.has(selector)) elements.set(selector, element()); return elements.get(selector); };
  const name = element(), form = element(), cancel = element();
  form.querySelector = selector => selector === '[name="name"]' ? name : get(`form:${selector}`);
  form.querySelectorAll = () => [name];
  name.name = 'name';
  get('#modal').querySelector = selector => selector === 'form' ? form : cancel;
  get('#list').querySelector = () => null;
  get('#lockForm').querySelector = () => get('#masterPassword');
  const context=vm.createContext({
    URL,
    MemBoomLibraryUI:{mount:options=>{library=options;return {render(){},setKind(){},clear(){}};}},
    document: { querySelector: get, addEventListener() {}, body: { insertAdjacentHTML() {} } },
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
    crypto: webcrypto, TextEncoder, TextDecoder, Uint8Array, Uint32Array, btoa, atob,
    CSS: { escape: value => value }, // Fixture IDs contain only safe ASCII letters and hyphens.
    setTimeout() { return 1; }, clearTimeout() {},
    FormData: class { constructor() { return [[name.name, name.value]][Symbol.iterator](); } },
    confirm:()=>true,...environment
  });
  vm.runInContext(librarySource,context);vm.runInContext(notesSource,context);vm.runInContext(noteLinksSource,context);
  context.MemBoomNoteLinks.createEditor=(_form,initial)=>{get('form:links').value=JSON.stringify(initial);return {read:()=>context.MemBoomNotes.links(JSON.parse(get('form:links').value)),dirty:()=>get('form:links').value!==JSON.stringify(initial)};};
  vm.runInContext(script,context);
  get('#masterPassword').value = 'test!123';
  await get('#lockForm').listeners.submit({ preventDefault() {} });
  const submit = async value => { name.value = value; await form.listeners.submit({ preventDefault() {} }); };
  const clickAction = async dataset => {
    const action = { dataset };
    await get('#list').listeners.click({ target: { closest: selector => selector === '[data-action]' ? action : null } });
  };
  const submitNote=async(title,content,pinned=false,links)=>{
    get('form:[name="title"]').value=title;get('form:[name="content"]').value=content;get('form:[name="pinned"]').checked=pinned;
    if(links!==undefined)get('form:links').value=JSON.stringify(links);
    await form.listeners.submit({preventDefault(){}});
  };
  const clickNote=async(dataset)=>get('#notesList').listeners.click({target:{closest:selector=>selector==='[data-note-action]'?{dataset}:null}});
  const importLibrary=(rows,kind='anime')=>library.change(items=>{const merged=context.MemBoomLibrary.merge(items,context.MemBoomLibrary.importRecords(rows,kind));items.splice(0,items.length,...merged.items);});
  return { get, form, name, cancel, submit, submitNote,clickNote,storage, clickAction,importLibrary,getLibrary:()=>library.getItems(),changeLibrary:library.change };
}
