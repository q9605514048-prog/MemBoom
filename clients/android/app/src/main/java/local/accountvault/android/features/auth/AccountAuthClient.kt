package local.accountvault.android.features.auth

import com.google.gson.*
import java.net.*
import local.accountvault.android.*

data class AccountSession(val endpoint:String,val token:String,val expiresAt:String,val userId:String,val login:String)
class AccountAuthClient(private val endpoint:String,private val factory:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection}){
    init{val u=URI(endpoint);require(u.rawUserInfo==null&&u.rawQuery==null&&u.rawFragment==null&&(u.path.isNullOrEmpty()||u.path=="/")&&(u.scheme=="https"||(u.scheme=="http"&&u.host in listOf("localhost","127.0.0.1","::1","10.0.2.2","192.168.3.2")))){"Для внешнего сервера нужен HTTPS, адрес — без пути."}}
    fun login(login:String,password:String)=authenticate("login",login,password)
    fun register(login:String,password:String)=authenticate("register",login,password)
    private fun authenticate(path:String,login:String,password:String)=session(request(path,JsonObject().apply{addProperty("login",login);addProperty("password",password);addProperty("remember",true)}))
    fun logout(token:String){request("logout",JsonObject(),token)}
    private fun session(root:JsonObject):AccountSession{
        val u=root.getAsJsonObject("user");val token=root.get("token").asString;require(Regex("mbs_[A-Za-z0-9_-]{43}").matches(token))
        val expiry=root.get("expiresAt").asString;java.time.Instant.parse(expiry)
        require(!u.get("login").asString.isBlank()&&!u.get("id").asString.isBlank())
        return AccountSession(endpoint.trimEnd('/'),token,expiry,u.get("id").asString,u.get("login").asString)
    }
    private fun request(path:String,payload:JsonObject,bearer:String?=null):JsonObject{
        val c=factory(URL(endpoint.trimEnd('/')+"/api/auth/"+path))
        try{
            c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=20000;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
            if(bearer!=null){require(!bearer.contains('\r')&&!bearer.contains('\n'));c.setRequestProperty("Authorization","Bearer $bearer")}
            c.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}
            val code=c.responseCode;if(code==204)return JsonObject()
            val bytes=java.io.ByteArrayOutputStream()
            (if(code in 200..299)c.inputStream else c.errorStream)?.use{stream->val buffer=ByteArray(4096);while(true){val count=stream.read(buffer);if(count<0)break;require(bytes.size()+count<=32768);bytes.write(buffer,0,count)}}
            val j=JsonParser.parseString(bytes.toString("UTF-8")).asJsonObject
            if(code !in 200..299)throw IllegalStateException(message(j.get("error")?.asString));return j
        }finally{c.disconnect()}
    }
    companion object{fun message(e:String?)=when(e){"invalid_credentials"->"Неверный логин или пароль.";"login_unavailable"->"Этот логин уже используется.";"too_many_attempts"->"Слишком много попыток. Подождите перед повтором.";"invalid_registration"->"Проверьте логин и пароль.";else->"Не удалось выполнить вход. Проверьте сервер и подключение."}}
}
object AccountAuthStore{
    private const val KEY="session"
    fun load(c:android.content.Context):AccountSession?{val v=c.getSharedPreferences("remote-account",0).getString(KEY,null)?:return null;return Gson().fromJson(EncryptedQueueSnapshot.decrypt(v,QueueKeyStore.getOrCreate()),AccountSession::class.java)}
    fun save(c:android.content.Context,s:AccountSession){val v=EncryptedQueueSnapshot.encrypt(Gson().toJson(s),QueueKeyStore.getOrCreate());check(c.getSharedPreferences("remote-account",0).edit().putString(KEY,v).commit())}
    fun clear(c:android.content.Context){check(c.getSharedPreferences("remote-account",0).edit().remove(KEY).commit())}
}
