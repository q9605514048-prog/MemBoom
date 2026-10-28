package local.accountvault.android.features.auth

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import local.accountvault.android.*
import local.accountvault.android.ui.MobileUi
import local.accountvault.android.features.security.*
import java.util.concurrent.Executors

class AccountActivity:Activity(){
    private val worker=Executors.newSingleThreadExecutor()
    private var mode="welcome"
    private var busy=false
    private var endpoint=""
    private lateinit var root:LinearLayout
    private lateinit var status:TextView
    private val startup get()=intent.getBooleanExtra("startup",false)
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        if(!startup&&!AppSession.unlocked){finish();return}
        if(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE==0)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        endpoint=try{AccountAuthStore.load(this)?.endpoint?:endpoint}catch(_:Exception){endpoint}
        draw()
    }
    private fun label(value:String,title:Boolean=false)=TextView(this).apply{
        text=value;textSize=if(title)28f else 14f;setTextColor(Color.parseColor(AndroidThemePolicy.mutedText));setPadding(0,MobileUi.dp(this,10),0,MobileUi.dp(this,12));if(title)MobileUi.title(this)
    }
    private fun button(value:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{text=value;MobileUi.button(this,primary);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=MobileUi.dp(root,10)};setOnClickListener{if(!busy)action()}}
    private fun draw(){
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;val pad=MobileUi.dp(this,24);setPadding(pad,MobileUi.dp(this,48),pad,pad);setBackgroundColor(Color.parseColor(AndroidThemePolicy.background))}
        root.addView(label("✦  MEMBOOM").apply{setTextColor(Color.parseColor(AndroidThemePolicy.magicGold));letterSpacing=0.12f;textSize=12f})
        root.addView(label(if(mode=="welcome")"Добро пожаловать в MemBoom" else if(mode=="register")"Создать аккаунт" else "Войти в MemBoom",true))
        root.addView(label(if(mode=="welcome")"Ваши аккаунты и заметки в одном уютном месте." else "Войдите один раз — приложение запомнит этот телефон."))
        status=label("")
        if(mode=="welcome"){
            root.addView(button("Создать аккаунт",true){mode="register";draw()})
            root.addView(button("Войти"){mode="login";draw()})
            root.addView(button("Попробовать без сервера"){
                try{
                    check(getSharedPreferences("app-security",MODE_PRIVATE).edit().putBoolean("local-mode",true).commit())
                    startActivity(Intent(this,UnlockActivity::class.java).putExtra("force_unlock",true));finish()
                }catch(_:Exception){status.text="Не удалось сохранить локальный режим."}
            })
            root.addView(label("Аккаунты и заметки останутся на телефоне. Сервер можно подключить позже."))
        }else{
            fun field(hintValue:String,secret:Boolean=false):EditText=EditText(this).apply{
                hint=hintValue;inputType=if(secret)129 else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;setSingleLine(true);MobileUi.field(this);root.addView(this,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,MobileUi.dp(this@AccountActivity.root,10),0,MobileUi.dp(this@AccountActivity.root,8))})
            }
            val address=field("Адрес сервера MemBoom").apply{setText(endpoint)}
            val login=field("Логин")
            val password=field("Пароль",true)
            val repeat=if(mode=="register")field("Повторите пароль",true)else null
            root.addView(button(if(mode=="register")"Создать аккаунт" else "Войти",true){
                if(repeat!=null&&repeat.text.toString()!=password.text.toString()){status.text="Пароли не совпадают";return@button}
                val action=mode;val value=password.text.toString();val name=login.text.toString();endpoint=address.text.toString().trim()
                busy=true;status.text="Выполняем вход…"
                worker.execute{
                    try{
                        val client=AccountAuthClient(endpoint);val account=if(action=="register")client.register(name,value)else client.login(name,value)
                        runOnUiThread{if(!isFinishing&&!isDestroyed){busy=false;password.text.clear();repeat?.text?.clear();complete(account,value)}}
                    }catch(error:Exception){runOnUiThread{if(!isFinishing&&!isDestroyed){busy=false;status.text=error.message?:"Не удалось подключиться к серверу."}}}
                }
            })
            root.addView(button("Назад"){endpoint=address.text.toString().trim();mode="welcome";draw()})
        }
        root.addView(status)
        val saved=try{AccountAuthStore.load(this)}catch(_:Exception){null}
        if(saved!=null&&!startup){
            root.addView(label("Подключён аккаунт: ${saved.login}"))
            root.addView(button("Выйти из аккаунта"){
                try{
                    AccountAuthStore.clear(this@AccountActivity);AppSession.unlocked=false
                    Thread{try{AccountAuthClient(saved.endpoint).logout(saved.token)}catch(_:Exception){}}.apply{isDaemon=true}.start()
                    startActivity(Intent(this,AccountActivity::class.java).putExtra("startup",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));finish()
                }catch(_:Exception){status.text="Не удалось удалить сохранённый вход."}
            })
        }
        if(!startup)root.addView(button("Закрыть"){finish()})
        setContentView(ScrollView(this).apply{isFillViewport=true;addView(root);MobileUi.keyboardInsets(this)})
    }
    private fun complete(account:AccountSession,password:String){
        val prefs=getSharedPreferences("app-security",MODE_PRIVATE)
        val old=prefs.getString("lock",null)
        if(old==null){
            busy=true;worker.execute{
                try{val key=QueueKeyStore.getOrCreate();val verifier=AppLockVerifier.create(password.toCharArray());check(prefs.edit().putString("lock",EncryptedQueueSnapshot.encrypt(verifier,key)).commit());runOnUiThread{if(!isDestroyed&&!isFinishing){busy=false;accept(account)}}}
                catch(_:Exception){runOnUiThread{if(!isDestroyed){busy=false;status.text="Не удалось подготовить локальное хранилище."}}}
            }
        }else if(AppSession.unlocked)accept(account)
        else{
            val field=EditText(this).apply{hint="Мастер-пароль прежних записей";inputType=129;MobileUi.field(this)}
            val dialog=AlertDialog.Builder(this).setTitle("Прежние локальные записи").setMessage("Один раз откройте старое хранилище мастер-паролем. Следующие запуски будут автоматическими.").setView(field).setPositiveButton("Открыть",null).setNegativeButton("Отмена",null).create()
            dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                val chars=field.text.toString().toCharArray();field.text.clear();busy=true
                worker.execute{
                    val valid=try{AppLockVerifier.verify(EncryptedQueueSnapshot.decrypt(old,QueueKeyStore.getOrCreate()),chars)}catch(_:Exception){false}finally{chars.fill('\u0000')}
                    runOnUiThread{if(!isDestroyed){busy=false;if(valid){dialog.dismiss();accept(account)}else field.error="Неверный мастер-пароль"}}
                }
            }};dialog.show()
        }
    }
    private fun accept(account:AccountSession){
        if(isDestroyed||isFinishing)return
        try{AccountAuthStore.save(this,account);getSharedPreferences("app-security",MODE_PRIVATE).edit().putBoolean("local-mode",false).apply();AppSession.unlocked=true;if(startup)startActivity(Intent(this,MainActivity::class.java));finish()}
        catch(_:Exception){status.text="Не удалось сохранить вход."}
    }
    override fun onDestroy(){worker.shutdownNow();if(::root.isInitialized)root.removeAllViews();super.onDestroy()}
}
