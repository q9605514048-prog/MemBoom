import {createServer} from 'node:http';

// Explicit opt-in for this installation's trusted home network only.
export function createHomeListener(server,host){
  if(host!=='192.168.3.2')throw Error('Unsupported home interface');
  return createServer((request,response)=>{
    const address=request.socket.remoteAddress;
    if(!/^192\.168\.3\.(?:[1-9]|[1-9]\d|1\d\d|2[0-4]\d|25[0-4])$/.test(address||'')){
      response.writeHead(403);response.end();return;
    }
    // Preserve one shared account, session, rate-limit and vault state.
    server.emit('request',request,response);
  });
}
