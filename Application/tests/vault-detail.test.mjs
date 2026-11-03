import {test} from 'node:test';
import assert from 'node:assert/strict';
import {openVault} from './helpers/vault-harness.mjs';
const seed=[{id:'work',name:'Work',accounts:[{id:'mail',name:'Synthetic account',favorite:true,fields:[{label:'Login',value:'demo@example.test'},{label:'Password',value:'SyntheticOnly!123'},{label:'Recovery code',value:'SYNTHETIC-RECOVERY'}]}]}];
test('account list and dashboard do not expose secrets while details mask every sensitive field',async()=>{
 const app=await openVault(new Map([['my-info-v1',JSON.stringify(seed)]]));
 for(const id of ['#list','#dashboardContent']){
  assert.doesNotMatch(app.get(id).innerHTML,/SyntheticOnly|SYNTHETIC-RECOVERY|demo@example/);
  assert.match(app.get(id).innerHTML,/Synthetic account/);
 }
 await app.clickAction({action:'view-account',id:'mail',category:'work'});
 const detail=app.get('#accountDetail').innerHTML;
 assert.match(detail,/type="password" readonly value="SyntheticOnly!123"/);
 assert.match(detail,/type="password" readonly value="SYNTHETIC-RECOVERY"/);
 assert.match(detail,/demo@example.test/);
 app.get('#lockBtn').listeners.click();
 for(const id of ['#accountDetail','#list','#dashboardContent'])assert.doesNotMatch(app.get(id).innerHTML,/SyntheticOnly|SYNTHETIC-RECOVERY|Synthetic account/);
});
test('navigation retains selected records without mutating the encrypted vault',async()=>{
 const app=await openVault(new Map([['my-info-v1',JSON.stringify(seed)]]));
 const before=app.storage.get('vault-encrypted');
 app.get('#backupTab').onclick();assert.equal(app.get('#backupPanel').hidden,false);assert.equal(app.get('#accountsPanel').hidden,true);
 app.get('#accountsTab').onclick();await app.clickAction({action:'view-account',id:'mail',category:'work'});
 assert.equal(app.storage.get('vault-encrypted'),before);
});
