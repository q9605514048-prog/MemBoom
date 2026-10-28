import {createHash,createHmac,randomBytes,randomInt,randomUUID,scrypt,timingSafeEqual} from 'node:crypto';
import {promisify} from 'node:util';
import {existsSync,mkdirSync,readFileSync,renameSync,writeFileSync} from 'node:fs';
import {dirname} from 'node:path';

const derive=promisify(scrypt),SESSION_MS=12*60*60*1000;
const hash=value=>createHash('sha256').update(value).digest('hex');
const validPassword=value=>typeof value==='string'&&/^[\x21-\x7e]{4,256}$/.test(value);
const validLogin=value=>typeof value==='string'&&/^[a-z0-9_.-]{3,32}$/.test(value);
class AuthError extends Error{constructor(status,code){super(code);this.status=status;}}
const fail=(status,code)=>{throw new AuthError(status,code);};
export class Accounts {
  constructor({storagePath,sendCode=null,clock=()=>Date.now(),secret}){
    this.path=storagePath;this.sendCode=sendCode;this.now=clock;this.secret=secret;this.rates=new Map();this.derivations=0;
    this.state={version:1,users:[],sessions:[],challenges:[]};
    if(storagePath&&existsSync(storagePath)){
      const data=JSON.parse(readFileSync(storagePath,'utf8'));
      if(data.version!==1||!Array.isArray(data.users)||!Array.isArray(data.sessions)||!Array.isArray(data.challenges)||data.users.some(u=>!/^[-a-f0-9]{36}$/.test(u.id)||typeof u.login!=='string'||typeof u.passwordHash!=='string'||typeof u.salt!=='string'))throw Error('Invalid account store; preserve the file and restore a backup');
      this.state=data;
    }
  }
  persist(){
    if(!this.path)return;mkdirSync(dirname(this.path),{recursive:true});
    const temporary=this.path+'.tmp';writeFileSync(temporary,JSON.stringify(this.state),{encoding:'utf8',mode:0o600});renameSync(temporary,this.path);
  }
  limit(key,max,windowMs){
    const now=this.now();if(this.rates.size>10000)for(const [k,v] of this.rates)if(v.until<=now)this.rates.delete(k);
    if(this.rates.size>10000)fail(429,'too_many_attempts');
    const current=this.rates.get(key);const rate=current&&current.until>now?current:{count:0,until:now+windowMs};
    rate.count++;this.rates.set(key,rate);if(rate.count>max)fail(429,'too_many_attempts');
  }
  prune(){const now=this.now();this.state.sessions=this.state.sessions.filter(s=>s.expiresAt>now);this.state.challenges=this.state.challenges.filter(c=>c.expiresAt>now);}
  publicUser(user){return {id:user.id,login:user.login};}
  authenticate(token){
    if(typeof token!=='string'||!/^mbs_[A-Za-z0-9_-]{43}$/.test(token))return null;
    const session=this.state.sessions.find(s=>s.hash===hash(token)&&s.expiresAt>this.now());
    return session?this.state.users.find(u=>u.id===session.userId)??null:null;
  }
  get userIds(){return this.state.users.map(u=>u.id);}
  async passwordHash(password,salt){
    if(this.derivations>=8)fail(429,'too_many_attempts');this.derivations++;
    try{return (await derive(password,salt,64,{N:16384,r:8,p:1,maxmem:32*1024*1024})).toString('hex');}finally{this.derivations--;}
  }
  issue(user,remember=false){
    this.prune();const token='mbs_'+randomBytes(32).toString('base64url');
    const active=this.state.sessions.filter(s=>s.userId===user.id).sort((a,b)=>a.expiresAt-b.expiresAt);
    if(active.length>=20){const oldest=active[0].hash;this.state.sessions=this.state.sessions.filter(s=>s.hash!==oldest);}
    const expiresAt=this.now()+(remember===true?365*24*60*60*1000:SESSION_MS);this.state.sessions.push({hash:hash(token),userId:user.id,expiresAt});this.persist();
    return {token,expiresAt:new Date(expiresAt).toISOString(),user:this.publicUser(user)};
  }
  async dispatch(path,body,token,ip){
    if(path==='/api/auth/config')return {status:200,body:{registrationEnabled:true,emailCodeEnabled:false}};
    if(path==='/api/auth/me'){const user=this.authenticate(token);if(!user)fail(401,'unauthorized');return {status:200,body:{user:this.publicUser(user)}};}
    if(path==='/api/auth/logout'){this.state.sessions=this.state.sessions.filter(s=>s.hash!==hash(token||''));this.persist();return {status:204,body:null};}
    if(path==='/api/auth/register'){
      this.limit('register:'+ip,10,60*60*1000);
      const login=typeof body.login==='string'?body.login.trim().toLowerCase():'';
      if(!validLogin(login)||!validPassword(body.password))fail(400,'invalid_registration');
      const unavailable=()=>this.state.users.some(u=>u.login===login);
      if(unavailable())fail(409,'login_unavailable');if(this.state.users.length>=2000)fail(503,'registration_unavailable');
      const salt=randomBytes(16).toString('hex'),passwordHash=await this.passwordHash(body.password,salt);
      if(unavailable())fail(409,'login_unavailable');
      const user={id:randomUUID(),login,salt,passwordHash,createdAt:new Date(this.now()).toISOString()};this.state.users.push(user);this.persist();
      return {status:201,body:this.issue(user,body.remember===true)};
    }
    if(path==='/api/auth/login'){
      const identifier=typeof body.login==='string'?body.login.trim().toLowerCase():'';
      this.limit('login-ip:'+ip,30,10*60000);
      if(!validPassword(body.password)||!validLogin(identifier))fail(401,'invalid_credentials');
      this.limit('login-id:'+identifier,8,10*60000);
      const user=this.state.users.find(u=>u.login===identifier);
      const proof=await this.passwordHash(body.password,user?.salt??'00000000000000000000000000000000');
      if(!user||!timingSafeEqual(Buffer.from(proof,'hex'),Buffer.from(user.passwordHash,'hex')))fail(401,'invalid_credentials');
      return {status:200,body:this.issue(user,body.remember===true)};
    }
    fail(404,'not_found');
  }
  async handle(request,response){
    const path=request.url,method=path==='/api/auth/me'||path==='/api/auth/config'?'GET':'POST';
    const send=(status,value)=>{if(response.destroyed)return;response.writeHead(status,{'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'});response.end(value===null?'':JSON.stringify(value));};
    try{
      if(request.method!==method)fail(405,'method_not_allowed');
      let body={};if(method==='POST'){const chunks=[];let bytes=0;for await(const chunk of request){bytes+=chunk.length;if(bytes>16384)fail(413,'payload_too_large');chunks.push(chunk);}body=JSON.parse(Buffer.concat(chunks).toString('utf8'));if(!body||typeof body!=='object'||Array.isArray(body))fail(400,'invalid_json');}
      const bearer=request.headers.authorization?.startsWith('Bearer ')?request.headers.authorization.slice(7):'';
      const result=await this.dispatch(path,body,bearer,request.socket.remoteAddress||'unknown');send(result.status,result.body);
    }catch(error){send(error.status??(error instanceof SyntaxError?400:503),{error:error instanceof AuthError?error.message:error instanceof SyntaxError?'invalid_json':'auth_unavailable'});}
  }
}
