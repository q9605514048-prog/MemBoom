/* Device-only unlock. The master password is encrypted; the AES key is not exportable. */
globalThis.MemBoomRemember=(()=>{
  function browserStorage(){
    function operation(mode,action){return new Promise((resolve,reject)=>{
      if(!globalThis.indexedDB){reject(Error('Device storage unavailable'));return;}
      const opening=indexedDB.open('memboom-device-unlock-v1',1);
      opening.onupgradeneeded=()=>opening.result.createObjectStore('device');
      opening.onerror=()=>reject(Error('Device storage unavailable'));
      opening.onblocked=()=>reject(Error('Device storage blocked'));
      opening.onsuccess=()=>{
        const db=opening.result;db.onversionchange=()=>db.close();
        let result;const transaction=db.transaction('device',mode);
        const request=action(transaction.objectStore('device'));
        request.onsuccess=()=>{result=request.result};
        transaction.oncomplete=()=>{db.close();resolve(result)};
        transaction.onabort=transaction.onerror=()=>{db.close();reject(Error('Device storage failed'))};
      };
    });}
    return {read:()=>operation('readonly',s=>s.get('unlock')),write:value=>operation('readwrite',s=>s.put(value,'unlock')),remove:()=>operation('readwrite',s=>s.delete('unlock'))};
  }
  function create({storage=browserStorage(),crypto=globalThis.crypto}={}){
    let generation=0,queue=Promise.resolve();
    function serialize(work){const next=queue.catch(()=>{}).then(work);queue=next;return next;}
    async function save(password,session=null){
      if(typeof password!=='string'||!password)throw Error('Invalid unlock secret');
      const epoch=generation;
      const key=await crypto.subtle.generateKey({name:'AES-GCM',length:256},false,['encrypt','decrypt']);
      const nonce=crypto.getRandomValues(new Uint8Array(12));
      const ciphertext=await crypto.subtle.encrypt({name:'AES-GCM',iv:nonce},key,new TextEncoder().encode(JSON.stringify({password,session})));
      return serialize(async()=>{if(epoch!==generation)return false;await storage.write({version:1,key,nonce,ciphertext});return epoch===generation;});
    }
    async function load(){
      const epoch=generation;await queue.catch(()=>{});const record=await storage.read();
      if(!record||epoch!==generation)return null;
      if(record.version!==1||record.key?.extractable!==false)throw Error('Invalid remembered unlock');
      const clear=await crypto.subtle.decrypt({name:'AES-GCM',iv:record.nonce},record.key,record.ciphertext);
      const result=JSON.parse(new TextDecoder().decode(clear));
      if(typeof result.password!=='string'||!result.password)throw Error('Invalid remembered unlock');
      return epoch===generation?result:null;
    }
    function clear(){generation++;return serialize(()=>storage.remove());}
    return {save,load,clear};
  }
  return {...create(),create};
})();
