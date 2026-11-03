package local.accountvault.android.features.security

import local.accountvault.android.ui.I18n

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import local.accountvault.android.*
import java.util.concurrent.Executors
import local.accountvault.android.ui.MobileUi
import local.accountvault.android.ui.PasswordControls

class UnlockActivity : local.accountvault.android.ui.LanguageActivity() {
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
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;val pad=MobileUi.dp(this,24);setPadding(pad,MobileUi.dp(this,24),pad,pad);setBackgroundColor(Color.parseColor(AndroidThemePolicy.background)) }
        fun label(value: String,size: Float=14f)=TextView(this).apply {text=value;textSize=size;setTextColor(Color.parseColor(AndroidThemePolicy.mutedText));setPadding(0,MobileUi.dp(this,8),0,MobileUi.dp(this,16))}
        form.addView(MobileUi.brand(this))
        form.addView(label(if(stored==null)I18n.t("Защитите хранилище") else I18n.t("С возвращением"),28f).apply{MobileUi.title(this)})
        form.addView(label(if(stored==null)I18n.t("Придумайте пароль от 4 символов: английские буквы, цифры и спецсимволы.") else I18n.t("Введите мастер-пароль, чтобы открыть аккаунты и заметки.")))
        fun makePassword(hintText:String)=EditText(this).apply {hint=hintText;inputType=129;MobileUi.field(this);setSingleLine(true)}
        val password=makePassword(I18n.t("Мастер-пароль"))
        val confirmation=makePassword(I18n.t("Повторите мастер-пароль"))
        val credentials=MobileUi.stack(this,16).apply{background=MobileUi.shape(this)}
        credentials.addView(MobileUi.label(this,I18n.t("Мастер-пароль"),password));credentials.addView(password,MobileUi.gapParams(password))
        if(stored==null){credentials.addView(MobileUi.label(this,I18n.t("Повторите мастер-пароль"),confirmation));credentials.addView(confirmation,MobileUi.gapParams(confirmation))}
        credentials.addView(PasswordControls.reveal(this,password,I18n.t("Показать мастер-пароль")))
        form.addView(credentials,MobileUi.gapParams(credentials,20))
        val error=label("").apply{setTextColor(Color.parseColor(AndroidThemePolicy.danger));accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE}
        val open=Button(this).apply {text=if(stored==null)I18n.t("Создать и открыть") else I18n.t("Открыть хранилище");MobileUi.button(this,true)}
        form.addView(open,LinearLayout.LayoutParams(-1,-2));form.addView(error)
        form.addView(label(I18n.t("Мастер-пароль защищает локальный доступ. Сохраните его в надёжном месте.")))
        form.addView(MobileUi.section(this,I18n.t("Интерфейс"),"",I18n.languageButton(this),I18n.termsButton(this)))
        open.setOnClickListener {
            val chars=password.text.toString().toCharArray()
            if(!MasterPasswordSession.isValid(chars)){chars.fill('\u0000');error.text=I18n.t("Пароль: от 4 символов, без пробелов и кириллицы");return@setOnClickListener}
            if(stored==null&&!chars.contentEquals(confirmation.text.toString().toCharArray())){chars.fill('\u0000');error.text=I18n.t("Пароли не совпадают");return@setOnClickListener}
            open.isEnabled=false;password.text.clear();confirmation.text.clear();error.text=I18n.t("Открываем…")
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
                    else error.text=I18n.t("Не удалось открыть хранилище. Проверьте мастер-пароль.")
                }
            }
        }
        setContentView(ScrollView(this).apply {isFillViewport=true;addView(form);MobileUi.keyboardInsets(this)})
    }
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
}
