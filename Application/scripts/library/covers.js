globalThis.MemBoomLibraryCovers=(()=>{
const tr=(key,...args)=>globalThis.MemBoomI18n?.t(key,...args)??key.replace(/\{(\d+)\}/g,(_,n)=>String(args[+n]??''));

  function create(isUnlocked){
    const cache=new Map();let epoch=0,observer=null,controller=null;
    function cancel(){epoch++;observer?.disconnect();observer=null;controller?.abort();controller=null;}
    function clear(){cancel();cache.clear();}
    function show(grid,entries){
      cancel();if(!isUnlocked())return;
      const lease=epoch,byId=new Map(entries.map(x=>[x.Id,x])),queue=[];let working=0;
      controller=new AbortController();const signal=controller.signal;
      const current=()=>lease===epoch&&isUnlocked();
      function failed(img){if(current()&&img.hidden){const button=img.parentElement?.querySelector('[data-cover-retry]');if(button)button.hidden=false;}}
      function paint(img,value){
        if(!current()||!img.isConnected)return;
        try{const url=MemBoomLibrary.coverUrl(value);if(!url)return;
          img.addEventListener('load',()=>{if(current()){img.hidden=false;const button=img.parentElement?.querySelector('[data-cover-retry]');if(button)button.hidden=true;}},{once:true});
          img.addEventListener('error',()=>{if(current()){img.hidden=true;failed(img);}},{once:true});
          // Visibility is already handled by the container observer. A hidden lazy
          // image otherwise waits forever for layout, preventing its load event.
          img.loading='eager';img.src=url;
        }catch{}
      }
      async function work(){
        while(working<3&&queue.length&&current()){
          const {img,item}=queue.shift();working++;
          (async()=>{
            try{
              let data;
              for(let attempt=0;attempt<3;attempt++){
                try{const response=await fetch('/api/library/metadata?url='+encodeURIComponent(item.Url),{signal,credentials:'omit',referrerPolicy:'no-referrer'});
                  if(response.ok){data=await response.json();break;}
                  if(response.status===400)break;
                }catch(error){if(signal.aborted)throw error;}
                if(attempt<2)await new Promise(resolve=>setTimeout(resolve,250*(attempt+1)));
                if(!current())return;
              }
              if(!data){failed(img);return;}const url=MemBoomLibrary.coverUrl(data.coverUrl);
              if(current()){cache.set(item.Url,url);paint(img,url);}
            }catch{}finally{working--;if(current())work();}
          })();
        }
      }
      function load(img){
        const item=byId.get(img.dataset.libraryCover);if(!item)return;
        const button=img.parentElement?.querySelector('[data-cover-retry]');if(button)button.onclick=()=>{if(current()){button.hidden=true;cache.delete(item.Url);queue.push({img,item});work();}};
        const cached=cache.get(item.Url);if(cached){paint(img,cached);return;}
        if(item.CoverUrl)paint(img,item.CoverUrl);
        if(!['http:','https:'].includes(location.protocol))return;
        queue.push({img,item});work();
      }
      const images=grid.querySelectorAll('[data-library-cover]');
      if(typeof IntersectionObserver==='function'){
        // Observe the poster container; img itself starts hidden.
        observer=new IntersectionObserver(items=>{for(const item of items)if(item.isIntersecting){observer?.unobserve(item.target);const img=item.target.querySelector('[data-library-cover]');if(img)load(img);}},{rootMargin:'300px'});
        for(const img of images)observer.observe(img.parentElement);
      }else for(const img of images)load(img);
    }
    return {show,clear};
  }
  return {create};
})();
