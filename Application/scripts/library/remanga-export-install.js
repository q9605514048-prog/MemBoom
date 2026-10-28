(()=>{
  const link=document.querySelector('#exportBookmark'),code=document.querySelector('#bookmarkCode');
  const value=MemBoomRemangaExporter.bookmarklet();link.href=value;code.value=value;
  link.onclick=event=>{event.preventDefault();document.querySelector('#installHint').textContent='Перетащи эту кнопку на панель закладок. Запускай её на странице своих закладок ReManga.';};
  document.querySelector('#copyCode').onclick=async()=>{
    try{await navigator.clipboard.writeText(value);document.querySelector('#copyResult').textContent='Адрес скопирован. Вставь его в адрес новой закладки.';}
    catch{code.focus();code.select();document.querySelector('#copyResult').textContent='Код выделен. Нажми Ctrl+C и вставь его в адрес новой закладки.';}
  };
})();
