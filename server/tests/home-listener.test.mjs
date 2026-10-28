import {test} from 'node:test';
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {createHomeListener} from '../../tools/server/home-listener.mjs';

test('home listener rejects broad or unrelated interfaces',()=>{
  for(const host of ['0.0.0.0','::','172.19.0.1','192.168.3.20']){
    assert.throws(()=>createHomeListener(new EventEmitter(),host));
  }
});
test('only home subnet requests reach the shared application',()=>{
  const application=new EventEmitter();let calls=0;
  application.on('request',()=>calls++);
  const listener=createHomeListener(application,'192.168.3.2').listeners('request')[0];
  for(const remoteAddress of ['192.168.3.2','192.168.3.20','192.168.3.254']){
    listener({socket:{remoteAddress}},{writeHead(){assert.fail('home address denied')},end(){}});
  }
  assert.equal(calls,3);
  for(const remoteAddress of ['192.168.30.20','26.153.12.59','172.19.0.1','127.0.0.1','192.168.3.0','192.168.3.255',undefined]){
    let code;
    listener({socket:{remoteAddress}},{writeHead(value){code=value},end(){}});
    assert.equal(code,403);
  }
  assert.equal(calls,3);
});
