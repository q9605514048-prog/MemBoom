/* Presentation only. Never receives or returns key material. */
globalThis.MemBoomPresentation=(()=>{
 const isSecret=label=>! /^(логин|почта|e-?mail|username|user name|login|url|ссылка|сайт|website)$/iu.test(String(label).trim());
 function overview(categories,notes,library){
  const entries=categories.flatMap(c=>(c.accounts??[]).map(a=>({id:a.id,category:c.id,categoryName:c.name,name:a.name,favorite:a.favorite===true,updatedAt:a.updatedAt??a.UpdatedAt??null})));
  const dated=entries.filter(a=>a.updatedAt&&Number.isFinite(Date.parse(a.updatedAt))).sort((a,b)=>Date.parse(b.updatedAt)-Date.parse(a.updatedAt));
  return {accounts:entries.length,notes:notes.length,categories:categories.length,favorites:entries.filter(a=>a.favorite).length,entries:dated.length?dated.slice(0,5):entries.slice(-5).reverse(),dated:dated.length>0,pinned:notes.filter(n=>n.Pinned).map(n=>({id:n.Id,title:n.Title,updatedAt:n.UpdatedAt})).slice(0,4),manga:library.filter(x=>x.Kind==='manga').length,anime:library.filter(x=>x.Kind==='anime').length};
 }
 function sortNotes(notes,order){const result=[...notes];if(order==='title')return result.sort((a,b)=>a.Title.localeCompare(b.Title));if(order==='oldest')return result.sort((a,b)=>Date.parse(a.UpdatedAt)-Date.parse(b.UpdatedAt));return result.sort((a,b)=>Number(b.Pinned)-Number(a.Pinned)||Date.parse(b.UpdatedAt)-Date.parse(a.UpdatedAt));}
 return {isSecret,overview,sortNotes};
})();
