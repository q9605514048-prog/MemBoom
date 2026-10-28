package local.accountvault.android.features.security

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import local.accountvault.android.*
import java.util.concurrent.Executors
import local.accountvault.android.ui.MobileUi

class UnlockActivity : Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs=getSharedPreferences("app-security",MODE_PRIVATE)
        if(!intent.getBooleanExtra("force_unlock",false)){
            val saved=try{local.accountvault.android.features.auth.AccountAuthStore.load(this)}catch(_:Exception){null}
            if(local.accountvault.android.features.auth.StartupPolicy.mayResume(saved)){
                AppSession.unlocked=true;startActivity(Intent(this,MainActivity::class.java));finish();return
            }
            if(local.accountvault.android.features.auth.StartupPolicy.requiresAccountChoice(saved,prefs.getBoolean("local-mode",false))){
                startActivity(Intent(this,local.accountvault.android.features.auth.AccountActivity::class.java).putExtra("startup",true));finish();return
            }
        }
        val stored=prefs.getString("lock",null)
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;val pad=MobileUi.dp(this,24);setPadding(pad,MobileUi.dp(this,48),pad,pad);setBackgroundColor(Color.parseColor(AndroidThemePolicy.background)) }
        fun label(value: String,size: Float=14f)=TextView(this).apply {text=value;textSize=size;setTextColor(Color.parseColor(AndroidThemePolicy.mutedText));setPadding(0,MobileUi.dp(this,8),0,MobileUi.dp(this,16))}
        form.addView(label("✦  MEMBOOM",12f).apply{setTextColor(Color.parseColor(AndroidThemePolicy.magicGold));letterSpacing=0.12f})
        form.addView(label(if(stored==null)"Защитите хранилище" else "С возвращением",28f).apply{MobileUi.title(this)})
        form.addView(label(if(stored==null)"Придумайте пароль от 4 символов: английские буквы, цифры и спецсимволы." else "Введите мастер-пароль, чтобы открыть аккаунты и заметки."))
        fun makePassword(hintText:String)=EditText(this).apply {hint=hintText;inputType=129;MobileUi.field(this);setSingleLine(true)}
        val password=makePassword("Мастер-пароль")
        val confirmation=makePassword("Повторите мастер-пароль")
        form.addView(password)
        if(stored==null)form.addView(confirmation)
        val error=label("")
        val open=Button(this).apply {text=if(stored==null)"Создать и открыть" else "Открыть хранилище";MobileUi.button(this,true)}
        form.addView(open);form.addView(error)
        open.setOnClickListener {
            val chars=password.text.toString().toCharArray()
            if(!MasterPasswordSession.isValid(chars)){chars.fill('\u0000');error.text="Пароль: от 4 символов, без пробелов и кириллицы";return@setOnClickListener}
            if(stored==null&&!chars.contentEquals(confirmation.text.toString().toCharArray())){chars.fill('\u0000');error.text="Пароли не совпадают";return@setOnClickListener}
            open.isEnabled=false;password.text.clear();confirmation.text.clear();error.text="Открываем…"
            worker.execute {
                val success=try {
                    val key=QueueKeyStore.getOrCreate()
                    if(stored==null){
                        val record=AppLockVerifier.create(chars)
                        check(prefs.edit().putString("lock",EncryptedQueueSnapshot.encrypt(record,key)).commit())
                        true
                    }else AppLockVerifier.verify(EncryptedQueueSnapshot.decrypt(stored,key),chars)
                }catch(_:Exception){false}finally{chars.fill('\u0000')}
                runOnUiThread {
                    if(isFinishing||isDestroyed)return@runOnUiThread
                    open.isEnabled=true
                    if(success){AppSession.unlocked=true;startActivity(Intent(this,MainActivity::class.java));finish()}
                    else error.text="Не удалось открыть хранилище. Проверьте мастер-пароль."
                }
            }
        }
        setContentView(ScrollView(this).apply {isFillViewport=true;addView(form);MobileUi.keyboardInsets(this)})
    }
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
}
