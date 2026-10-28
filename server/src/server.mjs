import http from 'node:http';
import { createHash, createHmac, randomBytes, randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, renameSync } from 'node:fs';
import { dirname, resolve,extname,sep } from 'node:path';
import { pathToFileURL,fileURLToPath } from 'node:url';
import { Accounts } from './auth/accounts.mjs';
import {cachedMetadata,titleUrl} from './library/metadata.mjs';

const MAX_BODY_BYTES = 1_000_000;
const FORBIDDEN_KEYS = new Set(['password', 'secret', 'cleartext']);

function containsForbiddenKey(value) {
  if (!value || typeof value !== 'object') return false;
  if (Array.isArray(value)) return value.some(containsForbiddenKey);
  return Object.entries(value).some(([key, nested]) => FORBIDDEN_KEYS.has(key.toLowerCase()) || containsForbiddenKey(nested));
}

function tokenHash(token) { return createHash('sha256').update(token).digest('hex'); }

export function createServer({ token = process.env.VAULT_TOKEN, initialEnvelope = null, storagePath = process.env.VAULT_STORAGE_PATH, maxAuthFailures = 5, authBlockMs = 60_000,auth=null,onDevicesChanged=null,staticDirectory=null,allowedOrigins=[] } = {}) {
  if (!token || token.length < 16) throw new Error('A bearer token of at least 16 characters is required');
  let envelope = initialEnvelope;
  let revision = initialEnvelope ? 1 : 0;
  let conflicts = [];
  let devices = [];
  if (!initialEnvelope && storagePath) {
    try {
      const stored = JSON.parse(readFileSync(storagePath, 'utf8'));
      if (stored && Number.isSafeInteger(stored.revision) && stored.revision >= 0) {
        if (stored.envelope && typeof stored.envelope.ciphertext === 'string') {
          envelope = stored.envelope;
          revision = stored.revision;
        }
        conflicts = Array.isArray(stored.conflicts) ? stored.conflicts : [];
        devices = Array.isArray(stored.devices) ? stored.devices : [];
      }
    } catch {}
  }
  const authFailures = new Map();
  const accounts=auth?new Accounts({...auth,secret:createHmac('sha256',token).update('memboom-auth-v1').digest()}):null;
  const personal=new Map(),deviceOwners=new Map();
  const userVault=id=>{
    if(!personal.has(id)){
      const internalToken=createHmac('sha256',token).update('user-vault:'+id).digest('hex');
      const personalPath=auth.vaultDirectory?resolve(auth.vaultDirectory,id+'.json'):auth.storagePath?resolve(dirname(auth.storagePath),'users',id+'.json'):null;
      const child=createServer({token:internalToken,storagePath:personalPath,allowedOrigins,onDevicesChanged:items=>{
        for(const [hash,owner] of deviceOwners)if(owner===id)deviceOwners.delete(hash);
        for(const item of items)if(!item.revokedAt)deviceOwners.set(item.tokenHash,id);
      }});personal.set(id,{child,internalToken});
    }
    return personal.get(id);
  };
  if(accounts)for(const id of accounts.userIds)userVault(id);
  const notifyDevices=()=>onDevicesChanged?.(devices);
  notifyDevices();
  const persist = () => {
    if (!storagePath) return;
    mkdirSync(dirname(storagePath), { recursive: true });
    const temporaryPath = `${storagePath}.tmp`;
    writeFileSync(temporaryPath, JSON.stringify({ revision, envelope, conflicts, devices }), { encoding: 'utf8', mode: 0o600 });
    renameSync(temporaryPath, storagePath);
  };

  return http.createServer(async (request, response) => {
    const origin=request.headers.origin;
    if(origin){
      const sameOrigin=origin===`http://${request.headers.host}`||origin===`https://${request.headers.host}`;
      if(!sameOrigin&&!allowedOrigins.includes(origin)){response.writeHead(403,{'content-type':'application/json'});response.end('{"error":"origin_not_allowed"}');return;}
      response.setHeader('access-control-allow-origin',origin);response.setHeader('vary','Origin');
      if(request.method==='OPTIONS'){response.setHeader('access-control-allow-methods','GET, POST, PUT');response.setHeader('access-control-allow-headers','authorization, content-type');response.writeHead(204);response.end();return;}
    }
    if(staticDirectory&&request.method==='GET'&&request.url.startsWith('/api/library/metadata?')){
      const headers={'content-type':'application/json; charset=utf-8','cache-control':'no-store'};
      const value=new URL(request.url,'http://localhost').searchParams.get('url');
      try{titleUrl(value);}catch{response.writeHead(400,headers);response.end('{"error":"invalid_title_url"}');return;}
      try{const valueOut=await cachedMetadata(value);response.writeHead(200,headers);response.end(JSON.stringify(valueOut));}
      catch{response.writeHead(502,headers);response.end('{"error":"cover_unavailable"}');}return;
    }
    if(staticDirectory&&request.method==='GET'&&!request.url.startsWith('/api/')&&request.url!=='/health'){
      try{
        const pathname=decodeURIComponent(new URL(request.url,'http://localhost').pathname),base=resolve(staticDirectory),filename=resolve(base,'.'+(pathname==='/'?'/index.html':pathname));
        if(!filename.startsWith(base+sep)||!['.html','.js','.css'].includes(extname(filename)))throw Error();
        const bytes=readFileSync(filename);response.writeHead(200,{'content-type':{'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8'}[extname(filename)],'cache-control':'no-store','x-content-type-options':'nosniff','referrer-policy':'no-referrer'});response.end(bytes);
      }catch{response.writeHead(404);response.end('Not found');}return;
    }
    if(accounts&&request.url.startsWith('/api/auth/')){await accounts.handle(request,response);return;}
    if(accounts&&request.url.startsWith('/api/')){
      const bearer=request.headers.authorization?.startsWith('Bearer ')?request.headers.authorization.slice(7):'';
      const user=accounts.authenticate(bearer),owner=user?.id??deviceOwners.get(tokenHash(bearer));
      if(owner){const {child,internalToken}=userVault(owner);if(user)request.headers.authorization='Bearer '+internalToken;child.emit('request',request,response);return;}
    }
    const headers = { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' };
    const send = (status, value) => { response.writeHead(status, headers); response.end(JSON.stringify(value)); };
    if (request.url === '/health' && request.method === 'GET') return send(200, { status: 'ok' });
    const isDeviceRoute = request.url === '/api/devices' || /^\/api\/devices\/[^/]+\/revoke$/.test(request.url);
    const isVaultRoute = request.url === '/api/vault' || request.url === '/api/vault/resolve';
    if ((!isVaultRoute && !isDeviceRoute) || !['GET', 'PUT', 'POST'].includes(request.method)) return send(404, { error: 'not_found' });
    if (request.url === '/api/vault/resolve' && request.method !== 'POST') return send(404, { error: 'not_found' });
    if (request.url === '/api/vault' && !['GET', 'PUT'].includes(request.method)) return send(404, { error: 'not_found' });
    if (request.url === '/api/devices' && !['GET', 'POST'].includes(request.method)) return send(404, { error: 'not_found' });
    const clientAddress = request.socket.remoteAddress || 'unknown';
    const authorization = request.headers.authorization || '';
    const bearer = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';
    const isAdmin = authorization === `Bearer ${token}`;
    const device = bearer ? devices.find(item => item.tokenHash === tokenHash(bearer) && !item.revokedAt) : null;
    if (request.url !== '/health' && !isAdmin && !device) {
      let authState = authFailures.get(clientAddress);
      if (authState?.blockedUntil && authState.blockedUntil <= Date.now()) {
        authFailures.delete(clientAddress);
        authState = null;
      }
      if (authState && authState.blockedUntil > Date.now()) return send(429, { error: 'too_many_attempts' });
      const failures = (authState?.failures || 0) + 1;
      authFailures.set(clientAddress, { failures, blockedUntil: failures >= maxAuthFailures ? Date.now() + authBlockMs : 0 });
      return send(failures >= maxAuthFailures ? 429 : 401, { error: failures >= maxAuthFailures ? 'too_many_attempts' : 'unauthorized' });
    }
    if (isDeviceRoute && !isAdmin) return send(401, { error: 'unauthorized' });
    if (request.url === '/api/devices' && request.method === 'GET') {
      return send(200, devices.map(({ deviceId, name, createdAt, revokedAt }) => ({ deviceId, name, createdAt, revokedAt, revoked: Boolean(revokedAt) })));
    }
    if (isVaultRoute && request.method === 'GET') return send(200, { revision, envelope, conflicts });
    if (/^\/api\/devices\/[^/]+\/revoke$/.test(request.url)) {
      const deviceId = request.url.split('/')[3];
      const device = devices.find(item => item.deviceId === deviceId);
      if (!device) return send(404, { error: 'device_not_found' });
      device.revokedAt = new Date().toISOString();
      persist();
      notifyDevices();
      return send(200, { deviceId, revoked: true });
    }

    let body = '';
    try {
      for await (const chunk of request) {
        body += chunk;
        if (Buffer.byteLength(body) > MAX_BODY_BYTES) return send(413, { error: 'payload_too_large' });
      }
      const payload = JSON.parse(body);
      if (!payload || typeof payload !== 'object' || Array.isArray(payload) || containsForbiddenKey(payload)) {
        return send(400, { error: 'invalid_envelope' });
      }
      if (request.url === '/api/devices') {
        if (typeof payload.name !== 'string' || !payload.name.trim()) return send(400, { error: 'invalid_device' });
        const deviceId = randomUUID();
        const deviceToken = randomBytes(32).toString('base64url');
        devices.push({ deviceId, name: payload.name.trim(), tokenHash: tokenHash(deviceToken), createdAt: new Date().toISOString(), revokedAt: null });
        persist();
        notifyDevices();
        return send(201, { deviceId, deviceToken });
      }
      if (/^\/api\/devices\/[^/]+\/revoke$/.test(request.url)) {
        const deviceId = request.url.split('/')[3];
        const device = devices.find(item => item.deviceId === deviceId);
        if (!device) return send(404, { error: 'device_not_found' });
        device.revokedAt = new Date().toISOString();
        persist();
        return send(200, { deviceId, revoked: true });
      }
      if (request.url === '/api/vault/resolve') {
        const conflict = conflicts.find(item => item.id === payload.conflictId);
        if (!conflict) return send(404, { error: 'conflict_not_found' });
        envelope = conflict.envelope;
        revision++;
        conflicts = conflicts.filter(item => item.id !== conflict.id);
        persist();
        return send(200, { revision });
      }
      if (!Number.isSafeInteger(payload.revision) || !payload.envelope || typeof payload.envelope.ciphertext !== 'string' || !Number.isInteger(payload.envelope.version)) {
        return send(400, { error: 'invalid_envelope' });
      }
      if (payload.revision !== revision) {
        const conflict = { id: randomUUID(), revision: payload.revision, envelope: payload.envelope };
        conflicts.push(conflict);
        persist();
        return send(409, { error: 'revision_conflict', revision, envelope, conflict });
      }
      envelope = payload.envelope;
      revision++;
      persist();
      return send(200, { revision });
    } catch {
      return send(400, { error: 'invalid_json' });
    }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const token = process.env.VAULT_TOKEN;
  if (!token || token.length < 16) {
    console.error('Set VAULT_TOKEN to a random value of at least 16 characters.');
    process.exitCode = 1;
  } else {
    const port = Number(process.env.PORT || 8788);
    const host = process.env.VAULT_HOST || '127.0.0.1';
    const storagePath = process.env.VAULT_STORAGE_PATH || resolve(process.cwd(), 'data', 'vault.json');
    createServer({ token, storagePath,auth:{storagePath:resolve(dirname(storagePath),'accounts.json'),vaultDirectory:resolve(dirname(storagePath),'users')},staticDirectory:fileURLToPath(new URL('../../Application/',import.meta.url)),allowedOrigins:(process.env.AUTH_ALLOWED_ORIGINS||'').split(',').map(x=>x.trim()).filter(Boolean) }).listen(port, host, () => {
      console.log(`Local development server listening at http://${host}:${port}`);
    });
  }
}
