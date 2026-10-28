package local.accountvault.android

import local.accountvault.android.ui.I18n

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.FrameLayout
import local.accountvault.android.features.editor.AccountEntryService
import local.accountvault.android.features.editor.FieldsEditorView
import local.accountvault.android.features.security.PasswordGenerator
import java.util.concurrent.Executors
import android.content.Intent
import local.accountvault.android.features.backup.VaultBackup
import local.accountvault.android.features.security.AppSession
import local.accountvault.android.features.security.UnlockActivity
import local.accountvault.android.features.notes.NotesPanel
import local.accountvault.android.ui.MobileUi
import local.accountvault.android.features.auth.AccountActivity
import local.accountvault.android.features.auth.AccountAuthStore

class MainActivity : local.accountvault.android.ui.LanguageActivity() {
    private lateinit var queue: SyncQueue
    private lateinit var status: TextView
    private lateinit var accountsLayout: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var endpointInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var categoryInput: EditText
    private lateinit var accountInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var masterPasswordInput: EditText
    private lateinit var conflictIdInput: EditText
    private lateinit var accountsSummary: TextView
    private lateinit var customFields: FieldsEditorView
    private lateinit var notesPanel:NotesPanel
    private val pages = mutableListOf<ScrollView>()
    private val navigation = mutableListOf<Button>()
    private var latestSnapshot: VaultSnapshot? = null
    private var vault = LocalVault()
    private val syncExecutor = Executors.newSingleThreadExecutor()
    private var backupPickerActive = false
    private var accountScreenActive=false
    private var keyboardVisible=false
    private var currentPage=0
    private var pendingBackupJson: String? = null
    private var pendingBackupPassword: CharArray? = null
    private val lockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoLock = Runnable { lockVault() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backupPickerActive=savedInstanceState?.getBoolean("backup-picker-active",false)?:false
        if(!AppSession.unlocked){startActivity(Intent(this,UnlockActivity::class.java));finish();return}
        if(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val preferences = getPreferences(MODE_PRIVATE)
        val key = QueueKeyStore.getOrCreate()
        val encryptedQueue = preferences.getString(QUEUE_KEY, null)
        queue = if (encryptedQueue == null) {
            SyncQueue()
        } else {
            SyncQueue.fromSnapshot(EncryptedQueueSnapshot.decrypt(encryptedQueue, key))
        }
        val encryptedVault = preferences.getString(VAULT_KEY, null)
        vault = if (encryptedVault == null) LocalVault() else LocalVault.fromSnapshot(
            EncryptedQueueSnapshot.decrypt(encryptedVault, key)
        )
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor(AndroidThemePolicy.magicGold))
            setPadding(dp(2), dp(4), dp(2), dp(10))
            maxLines=3
            ellipsize=android.text.TextUtils.TruncateAt.END
            addTextChangedListener(object:android.text.TextWatcher{
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){visibility=if(s.isNullOrBlank())View.GONE else View.VISIBLE}
                override fun afterTextChanged(s:android.text.Editable?){}
            })
        }
        searchInput = EditText(this).apply {
            hint = I18n.t("Поиск по категориям, аккаунтам и полям")
            setSingleLine(true)
            applyMeguminFieldStyle()
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshAccounts() }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        endpointInput = EditText(this).apply {
            hint = I18n.t("Адрес сервера, например http://192.168.3.2:8788")
            setText(loadSettings()?.endpoint ?: "")
            applyMeguminFieldStyle()
        }
        tokenInput = EditText(this).apply {
            hint = I18n.t("Токен устройства")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(loadSettings()?.token ?: "")
            applyMeguminFieldStyle()
        }
        categoryInput = EditText(this).apply { hint = I18n.t("Категория");setSingleLine(true); applyMeguminFieldStyle() }
        accountInput = EditText(this).apply { hint = I18n.t("Название аккаунта");setSingleLine(true); applyMeguminFieldStyle() }
        emailInput = EditText(this).apply { hint = I18n.t("Почта / логин");setSingleLine(true); applyMeguminFieldStyle() }
        passwordInput = EditText(this).apply {
            hint = I18n.t("Пароль")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            applyMeguminFieldStyle()
        }
        masterPasswordInput = EditText(this).apply {
            hint = I18n.t("Мастер-пароль для синхронизации")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            applyMeguminFieldStyle()
        }
        conflictIdInput = EditText(this).apply { hint = I18n.t("ID конфликта для разрешения"); applyMeguminFieldStyle() }

        val addButton = Button(this).apply {
            text = I18n.t("Добавить аккаунт")
            applyMeguminButtonStyle(true)
            setOnClickListener {
                val category = categoryInput.text.toString().trim()
                val name = accountInput.text.toString().trim()
                if (category.isEmpty() || name.isEmpty()) {
                    status.text = I18n.t("Укажите категорию и название аккаунта")
                    return@setOnClickListener
                }
                try {
                    AccountEntryService(vault, ::persistVault).add(category, name, listOf(
                        VaultField(I18n.t("Почта / логин"), emailInput.text.toString()),
                        VaultField(I18n.t("Пароль"), passwordInput.text.toString())
                    ) + customFields.readFields())
                } catch (error: Exception) {
                    android.util.Log.e("MemBoom", "Local vault persistence failed", error)
                    status.text = I18n.t("Не удалось сохранить аккаунт. Введённые данные остались в форме.")
                    return@setOnClickListener
                }
                accountInput.text.clear()
                emailInput.text.clear()
                passwordInput.text.clear()
                customFields.clearFields()
                refreshAccounts()
                showPage(0)
                val queued = enqueueCurrentSnapshot()
                status.text = if (queued) I18n.t("Аккаунт сохранён. Ожидает синхронизации: {0}", queue.size())
                    else I18n.t("Аккаунт сохранён на устройстве. Синхронизацию можно настроить позже.")
            }
        }
        val registerDeviceButton = Button(this).apply {
            text = I18n.t("Зарегистрировать это устройство")
            applyMeguminButtonStyle()
            setOnClickListener { registerDevice() }
        }
        val saveSettingsButton = Button(this).apply {
            text = I18n.t("Сохранить настройки синхронизации")
            applyMeguminButtonStyle()
            setOnClickListener {
                try {
                    saveSettings(SyncSettings(endpointInput.text.toString().trim(), tokenInput.text.toString()))
                    status.text = I18n.t("Настройки синхронизации сохранены.\nОжидает синхронизации: {0}", queue.size())
                } catch (_: Exception) {
                    status.text = I18n.t("Не удалось сохранить настройки")
                }
            }
        }
        val syncButton = Button(this).apply {
            text = I18n.t("Синхронизировать")
            applyMeguminButtonStyle(true)
            setOnClickListener { syncPending() }
        }
        val keepLocalButton = Button(this).apply {
            text = I18n.t("Оставить локальную версию")
            applyMeguminButtonStyle()
            setOnClickListener {
                status.text = I18n.t("Локальная версия оставлена. Очередь сохранена: {0}", queue.size())
            }
        }
        val acceptServerButton = Button(this).apply {
            text = I18n.t("Принять серверную версию")
            applyMeguminButtonStyle(true)
            setOnClickListener { acceptServerConflict() }
        }
        accountsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        accountsSummary = caption("")
        val accountsPage = pageContent().apply {
            addView(heading(I18n.t("Ваши аккаунты"),26f))
            addView(caption(I18n.t("Логины, пароли и всё, что важно сохранить.")))
            addView(searchInput)
            addView(accountsSummary)
            addView(accountsLayout)
        }
        val showPassword = CheckBox(this).apply {
            text = I18n.t("Показать пароль")
            setTextColor(Color.parseColor(AndroidThemePolicy.mutedText))
            setOnCheckedChangeListener { _, checked ->
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or if (checked)
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                passwordInput.setSelection(passwordInput.text.length)
            }
        }
        val generatePassword = Button(this).apply {
            text = I18n.t("Сгенерировать пароль")
            applyMeguminButtonStyle()
            setOnClickListener {
                passwordInput.setText(PasswordGenerator.generate())
                status.text = I18n.t("Создан пароль из 20 символов: буквы, цифры и спецсимволы.")
            }
        }
        customFields=FieldsEditorView(this)
        val addPage = pageContent().apply {
            addView(heading(I18n.t("Новый аккаунт"),26f))
            addView(caption(I18n.t("Запись будет сохранена на этом телефоне.")))
            val extra=Button(this@MainActivity).apply{text=I18n.t("Дополнительные поля  ＋");MobileUi.quiet(this);setOnClickListener{customFields.visibility=if(customFields.visibility==View.GONE)View.VISIBLE else View.GONE;text=if(customFields.visibility==View.VISIBLE)I18n.t("Дополнительные поля  −") else I18n.t("Дополнительные поля  ＋")}}
            customFields.visibility=View.GONE
            addView(MobileUi.section(this@MainActivity,I18n.t("Основное"),"",caption(I18n.t("Категория")),categoryInput,caption(I18n.t("Название")),accountInput))
            addView(MobileUi.section(this@MainActivity,I18n.t("Данные для входа"),"",caption(I18n.t("Почта / логин")),emailInput,caption(I18n.t("Пароль")),passwordInput,showPassword,generatePassword,extra,customFields))
            addView(addButton,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(16)})
        }
        val settingsPage = pageContent().apply {
            addView(heading(I18n.t("Настройки"),26f))
            addView(I18n.languageButton(this@MainActivity),LinearLayout.LayoutParams(-1,-2))
            addView(caption(I18n.t("Ваш аккаунт, резервные копии и защита.")))
            val accountButton=Button(this@MainActivity).apply{text=I18n.t("Управление аккаунтом");applyMeguminButtonStyle();setOnClickListener{accountScreenActive=true;startActivity(Intent(this@MainActivity,AccountActivity::class.java))}}
            val loginLabel=try{AccountAuthStore.load(this@MainActivity)?.login}catch(_:Exception){null}
            addView(MobileUi.section(this@MainActivity,I18n.t("Аккаунт MemBoom"),loginLabel?.let{I18n.t("Вы вошли как {0}", it)}?:I18n.t("Войдите, чтобы подключить сервер."),accountButton))
            val importButton=Button(this@MainActivity).apply{text=I18n.t("Импорт резервной копии");applyMeguminButtonStyle(true);setOnClickListener{promptBackup(false)}}
            val exportButton=Button(this@MainActivity).apply{text=I18n.t("Экспорт резервной копии");applyMeguminButtonStyle();setOnClickListener{promptBackup(true)}}
            addView(MobileUi.section(this@MainActivity,I18n.t("Перенос данных"),I18n.t("Аккаунты и заметки в зашифрованном файле. Импорт сохранит существующие записи."),importButton,exportButton))
            val downloadButton=Button(this@MainActivity).apply{text=I18n.t("Загрузить с сервера");applyMeguminButtonStyle();setOnClickListener{downloadServerVersion()}}
            addView(MobileUi.section(this@MainActivity,I18n.t("Синхронизация"),I18n.t("Введите мастер-пароль хранилища сайта. ПК должен быть включён."),masterPasswordInput,syncButton,downloadButton))
            val advanced=pageContent().apply{
                addView(caption(I18n.t("Адрес сервера")));addView(endpointInput);addView(caption(I18n.t("Токен устройства")));addView(tokenInput);addView(registerDeviceButton);addView(saveSettingsButton)
                addView(heading(I18n.t("Конфликты версий"),18f));addView(caption(I18n.t("Выберите версию только после проверки конфликта.")));addView(conflictIdInput);addView(keepLocalButton);addView(acceptServerButton)
                visibility=View.GONE
            }
            addView(Button(this@MainActivity).apply{text=I18n.t("Дополнительные настройки  ＋");MobileUi.quiet(this);setOnClickListener{advanced.visibility=if(advanced.visibility==View.GONE)View.VISIBLE else View.GONE;text=if(advanced.visibility==View.VISIBLE)I18n.t("Дополнительные настройки  −") else I18n.t("Дополнительные настройки  ＋")}},LinearLayout.LayoutParams(-1,-2))
            addView(advanced)
            addView(Button(this@MainActivity).apply{text=I18n.t("Заблокировать хранилище");MobileUi.quiet(this);setTextColor(Color.parseColor(AndroidThemePolicy.magicGold));setOnClickListener{lockVault()}},LinearLayout.LayoutParams(-1,-2))
            addView(caption("MemBoom 0.9.0 • Android"))
        }
        notesPanel=NotesPanel(this,{vault},{persistVaultState(true)},{status.text=it})
        val content = FrameLayout(this).apply {
            listOf(accountsPage, addPage, notesPanel, settingsPage).forEach { page ->
                val scroll = ScrollView(this@MainActivity).apply {
                    isFillViewport = AndroidLayoutPolicy.fillViewport
                    isVerticalScrollBarEnabled = true
                    addView(page)
                }
                pages.add(scroll)
                addView(scroll, FrameLayout.LayoutParams(-1, -1))
            }
        }
        val navigationBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background=MobileUi.shape(this)
            setPadding(dp(4),dp(4),dp(4),dp(4))
            listOf(I18n.t("Аккаунты"), I18n.t("Добавить"), I18n.t("Заметки"), I18n.t("Настройки")).forEachIndexed { index, label ->
                val button = Button(this@MainActivity).apply {
                    text = label
                    isAllCaps = false
                    minWidth = 0
                    textSize = 12f
                    setOnClickListener { showPage(index) }
                }
                navigation.add(button)
                addView(button, LinearLayout.LayoutParams(0, dp(62), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.parseColor(AndroidThemePolicy.background))
            val brand=LinearLayout(this@MainActivity).apply{gravity=android.view.Gravity.CENTER_VERTICAL;setPadding(0,0,0,dp(8))}
            brand.addView(heading("MemBoom",21f),LinearLayout.LayoutParams(0,-2,1f))
            brand.addView(caption(I18n.t("✦  Ваше хранилище")).apply{setTextColor(Color.parseColor(AndroidThemePolicy.magicGold));textSize=11f})
            addView(brand)
            addView(status)
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(navigationBar,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
            setOnApplyWindowInsetsListener { view, insets ->
                applySystemInsets(view, insets)
                insets
            }
        }
        setContentView(layout)
        showPage((savedInstanceState?.getInt("selected-page",0)?:0).coerceIn(0,3))
        refreshAccounts()
        refreshStatus()
        resetLockTimer()
    }

    private fun lockVault(){
        if(isFinishing||isDestroyed)return
        AppSession.unlocked=false
        if(::notesPanel.isInitialized)notesPanel.clearSensitive()
        vault=LocalVault()
        pendingBackupPassword?.fill('\u0000');pendingBackupPassword=null
        startActivity(Intent(this,UnlockActivity::class.java).putExtra("force_unlock",true));finish()
    }
    private fun resetLockTimer(){lockHandler.removeCallbacks(autoLock);if(AppSession.unlocked)lockHandler.postDelayed(autoLock,5*60*1000L)}
    override fun onUserInteraction(){super.onUserInteraction();resetLockTimer()}
    override fun onStart(){
        super.onStart()
        if(::status.isInitialized&&!AppSession.unlocked){startActivity(Intent(this,UnlockActivity::class.java));finish()}
        accountScreenActive=false
        if(::endpointInput.isInitialized&&AppSession.unlocked){try{val saved=loadSettings();endpointInput.setText(saved?.endpoint?:"");tokenInput.setText(saved?.token?:"")}catch(_:Exception){}}
    }
    override fun onStop(){
        lockHandler.removeCallbacks(autoLock)
        if(!isChangingConfigurations&&!backupPickerActive&&!accountScreenActive){AppSession.unlocked=false;vault=LocalVault();if(::notesPanel.isInitialized)notesPanel.clearSensitive()}
        super.onStop()
    }

    private fun promptBackup(export: Boolean){
        if(!export){openBackupPicker(false);return}
        val form=pageContent()
        val password=EditText(this).apply{hint=I18n.t("Пароль резервной копии");inputType=129;applyMeguminFieldStyle()}
        val repeat=EditText(this).apply{hint=I18n.t("Повторите пароль");inputType=129;applyMeguminFieldStyle()}
        form.addView(caption(I18n.t("От 4 символов: английские буквы, цифры и спецсимволы. Сохраните пароль отдельно.")));form.addView(password)
        if(export)form.addView(repeat)
        val dialog=AlertDialog.Builder(this).setTitle(if(export)I18n.t("Экспорт копии") else I18n.t("Импорт копии")).setView(form).setNegativeButton(I18n.t("Отмена"),null).setPositiveButton(I18n.t("Продолжить"),null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val chars=password.text.toString().toCharArray()
            if(!MasterPasswordSession.isValid(chars)){chars.fill('\u0000');password.error=I18n.t("Минимум 4 символа, без пробелов и кириллицы");return@setOnClickListener}
            if(export&&!chars.contentEquals(repeat.text.toString().toCharArray())){chars.fill('\u0000');repeat.error=I18n.t("Пароли не совпадают");return@setOnClickListener}
            password.text.clear();repeat.text.clear();dialog.dismiss()
            if(export){
                val snapshot=LocalVault.fromSnapshot(vault.snapshot())
                status.text=I18n.t("Шифруем резервную копию…")
                syncExecutor.execute{
                    try{
                        val json=VaultBackup.export(snapshot,chars)
                        runOnUiThread{if(AppSession.unlocked&&!isDestroyed){pendingBackupJson=json;openBackupPicker(true)}}
                    }catch(_:Exception){runOnUiThread{if(AppSession.unlocked)status.text=I18n.t("Не удалось создать копию")}}
                    finally{chars.fill('\u0000')}
                }
            }else{pendingBackupPassword=chars;openBackupPicker(false)}
        }}
        dialog.show()
    }
    private fun openBackupPicker(export:Boolean){
        backupPickerActive=true
        lockHandler.removeCallbacks(autoLock)
        try{
            val intent=Intent(if(export)Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT).apply{
                addCategory(Intent.CATEGORY_OPENABLE);type=if(export)"application/json" else "*/*"
                if(export)putExtra(Intent.EXTRA_TITLE,"MemBoom-${java.time.LocalDate.now()}.json")
            }
            startActivityForResult(intent,if(export)410 else 411)
        }catch(_:Exception){backupPickerActive=false;pendingBackupPassword?.fill('\u0000');pendingBackupPassword=null;pendingBackupJson=null;status.text=I18n.t("Не удалось открыть выбор файла");resetLockTimer()}
    }
    @Deprecated("Android Activity result callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,result:Intent?){
        super.onActivityResult(requestCode,resultCode,result)
        if(requestCode!=410&&requestCode!=411)return
        backupPickerActive=false;resetLockTimer()
        val uri=result?.data
        if(resultCode!=RESULT_OK||uri==null){pendingBackupPassword?.fill('\u0000');pendingBackupPassword=null;pendingBackupJson=null;status.text=I18n.t("Операция отменена");return}
        if(requestCode==410){
            val json=pendingBackupJson?:return;pendingBackupJson=null
            syncExecutor.execute{
                val success=try{contentResolver.openOutputStream(uri,"wt")!!.use{it.write(json.toByteArray(Charsets.UTF_8))};true}catch(_:Exception){false}
                runOnUiThread{if(AppSession.unlocked)status.text=if(success)I18n.t("Зашифрованная копия сохранена") else I18n.t("Не удалось записать файл копии")}
            }
        }else{
            syncExecutor.execute{
                try{
                    val bytes=java.io.ByteArrayOutputStream()
                    contentResolver.openInputStream(uri)!!.use{stream->
                        val buffer=ByteArray(8192)
                        while(true){val count=stream.read(buffer);if(count<0)break;require(bytes.size()+count<=VaultBackup.MAX_BYTES);bytes.write(buffer,0,count)}
                    }
                    val json=bytes.toString("UTF-8")
                    runOnUiThread{if(AppSession.unlocked&&!isDestroyed)promptImportPassword(json)}
                }catch(_:Exception){runOnUiThread{if(AppSession.unlocked)status.text=I18n.t("Не удалось прочитать файл копии (до 8 МБ).")}}
            }
        }
    }
    override fun onSaveInstanceState(outState:Bundle){
        outState.putInt("selected-page",currentPage)
        outState.putBoolean("backup-picker-active",backupPickerActive)
        super.onSaveInstanceState(outState)
    }
    private fun promptImportPassword(json:String){
        val password=EditText(this).apply{hint=I18n.t("Пароль, заданный при экспорте на сайте");inputType=129;applyMeguminFieldStyle()}
        val dialog=AlertDialog.Builder(this).setTitle(I18n.t("Открыть резервную копию")).setView(password).setNegativeButton(I18n.t("Отмена"),null).setPositiveButton(I18n.t("Открыть"),null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val chars=password.text.toString().toCharArray()
            if(!MasterPasswordSession.isValid(chars)){chars.fill('\u0000');password.error=I18n.t("Минимум 4 символа");return@setOnClickListener}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
            syncExecutor.execute{
                try{val incoming=VaultBackup.restore(json,chars);runOnUiThread{if(AppSession.unlocked&&!isDestroyed){password.text.clear();dialog.dismiss();confirmImport(incoming)}}}
                catch(_:Exception){runOnUiThread{if(AppSession.unlocked&&!isDestroyed){dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true;password.error=I18n.t("Копия не открыта. Проверьте пароль, заданный при её экспорте.");status.text=I18n.t("Не удалось расшифровать копию; прежние данные сохранены.")}}}
                finally{chars.fill('\u0000')}
            }
        }};dialog.setOnDismissListener{password.text.clear()};dialog.show()
    }
    private fun confirmImport(incoming:LocalVault){
        if(incoming.accounts.isEmpty()&&incoming.notes.isEmpty()){
            AlertDialog.Builder(this).setTitle(I18n.t("В копии нет аккаунтов и заметок")).setMessage(I18n.t("На сайте создайте полную резервную копию и подтвердите включение заметок. Списки манги и аниме остаются на сайте.")).setPositiveButton(I18n.t("Понятно"),null).show();return
        }
        AlertDialog.Builder(this).setTitle(I18n.t("Добавить записи из копии?")).setMessage(I18n.t("В копии {0} аккаунтов и {1} заметок. Совпадающие записи будут пропущены, существующие останутся.", incoming.accounts.size, incoming.notes.size))
            .setNegativeButton(I18n.t("Отмена"),null).setPositiveButton(I18n.t("Добавить записи")){_,_->
                val before=vault.snapshot()
                try{val oldAccounts=vault.accounts.size;val oldNotes=vault.notes.size;VaultBackup.merge(vault,incoming);persistVault();refreshAccounts();notesPanel.refresh();showPage(if(incoming.accounts.isEmpty())2 else 0);status.text=I18n.t("Добавлено аккаунтов: {0}, заметок: {1}. Всего: {2} аккаунтов, {3} заметок.", vault.accounts.size-oldAccounts, vault.notes.size-oldNotes, vault.accounts.size, vault.notes.size)}
                catch(_:Exception){vault=LocalVault.fromSnapshot(before);status.text=I18n.t("Импорт не сохранён; прежние записи остались")}
            }.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun applySystemInsets(view: View, insets: android.view.WindowInsets) {
        val (top, bottom) = if (android.os.Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            val ime=insets.getInsets(android.view.WindowInsets.Type.ime())
            val shown=ime.bottom>bars.bottom
            (navigation.firstOrNull()?.parent as? View)?.visibility=if(shown)View.GONE else View.VISIBLE
            if(shown!=keyboardVisible){
                keyboardVisible=shown
                if(shown)view.post{view.findFocus()?.let{it.requestRectangleOnScreen(android.graphics.Rect(0,0,it.width,it.height),false)}}
            }
            bars.top to maxOf(bars.bottom,ime.bottom)
        } else insets.systemWindowInsetTop to insets.systemWindowInsetBottom
        view.setPadding(dp(16), dp(12) + top, dp(16), dp(8) + bottom)
    }

    private fun heading(value: String, size: Float = 20f) = TextView(this).apply {
        text = value; textSize = size
        MobileUi.title(this)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun caption(value: String) = TextView(this).apply {
        text = value; textSize = 12f
        setTextColor(Color.parseColor(AndroidThemePolicy.mutedText))
        setPadding(0, dp(4), 0, dp(6))
    }

    private fun pageContent() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(16))
    }

    private fun showPage(index: Int) {
        currentPage=index
        status.text=""
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(window.decorView.windowToken, 0)
        pages.forEachIndexed { i, page -> page.visibility = if (i == index) View.VISIBLE else View.GONE }
        if(index==2)notesPanel.refresh()
        pages[index].apply{animate().cancel();alpha=1f;translationY=0f;if(android.provider.Settings.Global.getFloat(contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f){alpha=0f;translationY=dp(8).toFloat();animate().alpha(1f).translationY(0f).setDuration(180).start()}}
        navigation.forEachIndexed { i, button ->
            MobileUi.navigation(button,i,i==index)
        }
    }

    private fun EditText.applyMeguminFieldStyle() {
        MobileUi.field(this)
    }

    private fun Button.applyMeguminButtonStyle(primary: Boolean = false) {
        MobileUi.button(this,primary)
    }

    private fun refreshAccounts() {
        accountsLayout.removeAllViews()
        val matches = vault.search(searchInput.text.toString())
        accountsSummary.text = if(searchInput.text.isNullOrBlank())I18n.t("Записей в хранилище: {0}", vault.accounts.size) else I18n.t("Найдено: {0} из {1}", matches.size, vault.accounts.size)
        if (matches.isEmpty()) {
            accountsLayout.addView(heading(if (vault.accounts.isEmpty()) I18n.t("Начните с первого аккаунта") else I18n.t("Ничего не найдено")))
            accountsLayout.addView(caption(if (vault.accounts.isEmpty()) I18n.t("Добавьте запись — она останется доступна без интернета.") else I18n.t("Попробуйте другое название, категорию или логин.")))
            accountsLayout.addView(Button(this).apply {
                text = if (vault.accounts.isEmpty()) I18n.t("Добавить первый аккаунт") else I18n.t("Сбросить поиск")
                applyMeguminButtonStyle(true)
                setOnClickListener { if (vault.accounts.isEmpty()) showPage(1) else searchInput.text.clear() }
            })
        }
        matches.forEach { account ->
            val card = pageContent().apply {
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(AndroidThemePolicy.panel))
                    setStroke(dp(1), Color.parseColor(AndroidThemePolicy.panelStrong))
                    cornerRadius = dp(14).toFloat()
                }
            }
            val accountRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val title = TextView(this).apply {
                text = account.name
                contentDescription=I18n.t("Открыть аккаунт {0} / {1}", account.category, account.name)
                textSize = 19f
                MobileUi.title(this)
                setPadding(0, dp(8), dp(8), dp(8))
                setTextColor(Color.parseColor(AndroidThemePolicy.text))
                setOnClickListener { showEditAccount(account) }
            }
            accountRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            accountRow.addView(Button(this).apply {
                text = I18n.t("Удалить")
                MobileUi.quiet(this)
                setTextColor(Color.parseColor("#EA8D96"))
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(I18n.t("Удалить аккаунт?"))
                        .setMessage("${account.category} / ${account.name}")
                        .setNegativeButton(I18n.t("Отмена"), null)
                        .setPositiveButton(I18n.t("Удалить")) { _, _ ->
                            try{AccountEntryService(vault,::persistVault).remove(account.id)}
                            catch(_:Exception){status.text=I18n.t("Не удалось удалить запись. Данные сохранены.");return@setPositiveButton}
                            enqueueCurrentSnapshot()
                            refreshAccounts()
                            status.text = I18n.t("Аккаунт удалён локально")
                        }
                        .show()
                }
            })
            card.addView(caption(account.category).apply{textSize=11f;setTextColor(Color.parseColor(AndroidThemePolicy.magicGold));setPadding(0,0,0,dp(2))})
            card.addView(accountRow)
            account.fields.forEach { field ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(TextView(this).apply {
                    text = "${field.label}\n${if (field.value.isEmpty()) "Не заполнено" else VaultDisplay.visibleValue(field)}"
                    setTextColor(Color.parseColor(AndroidThemePolicy.mutedText))
                    setPadding(0, dp(6), dp(8), dp(6))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(Button(this).apply {
                    text = I18n.t("Копировать")
                    contentDescription = I18n.t("Копировать {0} аккаунта {1}", field.label, account.name)
                    isEnabled = field.value.isNotEmpty()
                    MobileUi.quiet(this)
                    setOnClickListener {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(field.label, field.value))
                        window.decorView.postDelayed({
                            val current = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()
                            if (current == field.value) clipboard.setPrimaryClip(ClipData.newPlainText("MemBoom", ""))
                        }, ClipboardPolicy.clearDelayMs)
                        status.text = I18n.t("Скопировано поле: {0}", field.label)
                    }
                })
                card.addView(row)
            }
            accountsLayout.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
    }

    private fun showEditAccount(account: VaultAccount) {
        val category = EditText(this).apply { hint=I18n.t("Категория");setText(account.category);applyMeguminFieldStyle() }
        val name = EditText(this).apply { hint=I18n.t("Название аккаунта");setText(account.name);applyMeguminFieldStyle() }
        val fieldsEditor=FieldsEditorView(this,account.fields)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(category)
            addView(name)
            addView(fieldsEditor)
        }
        val dialog=AlertDialog.Builder(this)
            .setTitle(I18n.t("Изменить аккаунт"))
            .setView(ScrollView(this).apply{addView(form)})
            .setNegativeButton(I18n.t("Отмена"), null)
            .setPositiveButton(I18n.t("Сохранить"),null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if (category.text.toString().trim().isEmpty() || name.text.toString().trim().isEmpty()) {
                    name.error = I18n.t("Категория и название обязательны")
                    return@setOnClickListener
                }
                try{AccountEntryService(vault,::persistVault).update(account.id,category.text.toString(),name.text.toString(),fieldsEditor.readFields())}
                catch(_:Exception){name.error=I18n.t("Не удалось сохранить. Проверьте названия полей.");return@setOnClickListener}
                enqueueCurrentSnapshot()
                refreshAccounts()
                status.text = I18n.t("Аккаунт изменён локально")
                dialog.dismiss()
        }}
        dialog.show()
    }

    private fun refreshStatus() {
        status.text = if(queue.size()>0)I18n.t("Ожидает синхронизации: {0}", queue.size()) else ""
    }

    private fun enqueueCurrentSnapshot(): Boolean {
        val masterPassword = try {
            MasterPasswordSession.requireValid(masterPasswordInput.text.toString())
        } catch (_: IllegalArgumentException) {
            status.text = I18n.t("Изменение сохранено локально; задайте мастер-пароль для синхронизации")
            masterPasswordInput.text.clear()
            return false
        }
        try {
            val envelope = PortableVaultCrypto.encrypt(vault.toPortableJson(), masterPassword)
            queue.enqueue("local-${System.currentTimeMillis()}", PortableVaultCrypto.pack(envelope))
            persistQueue()
            return true
        } catch (_: Exception) {
            status.text = I18n.t("Данные сохранены локально; не удалось подготовить синхронизацию")
            return false
        } finally {
            masterPassword.fill('\u0000')
            masterPasswordInput.text.clear()
        }
    }

    private fun persistQueue() {
        val encrypted = EncryptedQueueSnapshot.encrypt(queue.snapshot(), QueueKeyStore.getOrCreate())
        getPreferences(MODE_PRIVATE).edit().putString(QUEUE_KEY, encrypted).apply()
    }

    private fun persistVault(){persistVaultState(true)}
    private fun persistVaultState(accountsChanged:Boolean) {
        val encrypted = EncryptedQueueSnapshot.encrypt(vault.snapshot(), QueueKeyStore.getOrCreate())
        val editor=getPreferences(MODE_PRIVATE).edit().putString(VAULT_KEY, encrypted)
        if(accountsChanged)editor.putBoolean("local-changes",true)
        check(editor.commit()) { "Local vault could not be written" }
    }

    private fun saveSettings(settings: SyncSettings) {
        val encrypted = EncryptedQueueSnapshot.encrypt(settings.encode(), QueueKeyStore.getOrCreate())
        getPreferences(MODE_PRIVATE).edit().putString(SETTINGS_KEY, encrypted).apply()
    }

    private fun loadSettings(): SyncSettings? {
        AccountAuthStore.load(this)?.let{return SyncSettings(it.endpoint,it.token)}
        return getPreferences(MODE_PRIVATE).getString(SETTINGS_KEY, null)?.let { SyncSettings.decode(EncryptedQueueSnapshot.decrypt(it, QueueKeyStore.getOrCreate())) }
    }

    private fun registerDevice() {
        val settings = try { loadSettings() } catch (_: Exception) { null }
        if (settings == null) {
            status.text = I18n.t("Сначала укажите endpoint и административный токен")
            return
        }
        val client = try { VaultSyncClient(settings.endpoint, settings.token) } catch (_: Exception) {
            status.text = I18n.t("Нужен HTTPS endpoint или loopback-адрес")
            return
        }
        status.text = I18n.t("Регистрация устройства выполняется…")
        SyncTaskRunner(syncExecutor).execute({
            val registration = client.registerDevice("MemBoom Android")
            saveSettings(SyncSettings(settings.endpoint, registration.deviceToken))
            runOnUiThread { tokenInput.setText(registration.deviceToken); status.text = I18n.t("Устройство зарегистрировано и токен сохранён зашифрованно") }
        }) { error ->
            if (error != null) runOnUiThread { status.text = I18n.t("Регистрация не выполнена: {0}", error.message ?: I18n.t("ошибка")) }
        }
    }

    private fun acceptServerConflict() {
        val settings = try { loadSettings() } catch (_: Exception) { null }
        val conflictId = conflictIdInput.text.toString().trim()
        if (settings == null || conflictId.isEmpty()) {
            status.text = I18n.t("Укажите настройки и ID конфликта")
            return
        }
        val client = try { VaultSyncClient(settings.endpoint, settings.token) } catch (_: Exception) {
            status.text = I18n.t("Нужен HTTPS endpoint или loopback-адрес")
            return
        }
        val conflict = latestSnapshot?.let { ConflictResolution.find(it, conflictId) }
        if (conflict == null) {
            status.text = I18n.t("Конфликт не найден. Сначала выполните синхронизацию")
            return
        }
        val masterPassword = try {
            MasterPasswordSession.requireValid(masterPasswordInput.text.toString())
        } catch (_: IllegalArgumentException) {
            status.text = I18n.t("Введите мастер-пароль от 4 ASCII-символов")
            masterPasswordInput.text.clear()
            return
        }
        status.text = I18n.t("Проверка серверной версии…")
        SyncTaskRunner(syncExecutor).execute({
            val decrypted = try {
                PortableVaultCrypto.decrypt(conflict.envelope, masterPassword)
            } finally {
                masterPassword.fill('\u0000')
            }
            val restoredVault = LocalVault.fromPortableJson(decrypted)
            check(ConflictResolution.acceptServerVersion(queue) {
                client.resolve(conflictId)
            }) { "Server conflict resolution failed; local queue was preserved" }
            VaultBackup.merge(restoredVault,LocalVault(notes=vault.notes.toMutableList()))
            vault = restoredVault
            persistVault()
            persistQueue()
            runOnUiThread {
                masterPasswordInput.text.clear()
                status.text = I18n.t("Серверная версия принята. Локальное хранилище обновлено.")
            }
        }) { error ->
            persistQueue()
            runOnUiThread {
                masterPasswordInput.text.clear()
                if (error != null) status.text = I18n.t("Не удалось принять серверную версию: {0}", error.message ?: I18n.t("ошибка"))
            }
        }
    }

    private fun downloadServerVersion(){
        AlertDialog.Builder(this).setTitle(I18n.t("Загрузить серверную версию?")).setMessage(I18n.t("Сначала будут проверены пароль и данные. После этого локальные записи заменятся серверной версией. Для сохранения обеих версий используйте резервную копию."))
            .setNegativeButton(I18n.t("Отмена"),null).setPositiveButton(I18n.t("Загрузить")){_,_->
                val settings=try{loadSettings()}catch(_:Exception){null}
                if(settings==null){status.text=I18n.t("Сначала сохраните настройки синхронизации");return@setPositiveButton}
                val chars=masterPasswordInput.text.toString().toCharArray()
                if(!MasterPasswordSession.isValid(chars)){chars.fill('\u0000');status.text=I18n.t("Введите мастер-пароль синхронизации от 4 символов");return@setPositiveButton}
                masterPasswordInput.text.clear();status.text=I18n.t("Проверяем серверную версию…")
                syncExecutor.execute{
                    try{
                        val snapshot=VaultSyncClient(settings.endpoint,settings.token).fetchSnapshot()
                        val envelope=snapshot.envelope?:throw IllegalStateException(I18n.t("На сервере пока нет копии. Сначала выполните синхронизацию на сайте."))
                        val restored=LocalVault.fromPortableJson(PortableVaultCrypto.decrypt(envelope,chars))
                        runOnUiThread{if(AppSession.unlocked&&!isDestroyed){
                            VaultBackup.merge(restored,LocalVault(notes=vault.notes.toMutableList()))
                            val before=vault;vault=restored
                            try{persistVault();queue.clear();persistQueue();getPreferences(MODE_PRIVATE).edit().putBoolean("local-changes",false).putLong("last-server-revision",snapshot.revision).commit();refreshAccounts();notesPanel.refresh();status.text=I18n.t("Загружено: {0} аккаунтов, {1} заметок", restored.accounts.size, restored.notes.size)}
                            catch(_:Exception){vault=before;status.text=I18n.t("Не удалось сохранить серверную версию; прежние записи остались")}
                        }}
                    }catch(error:Exception){runOnUiThread{if(AppSession.unlocked)status.text=if(error is javax.crypto.AEADBadTagException)I18n.t("Неверный мастер-пароль серверной копии. Используйте пароль хранилища сайта.") else error.message?:I18n.t("Серверная версия не загружена. Проверьте подключение и пароль.")}}
                    finally{chars.fill('\u0000')}
                }
            }.show()
    }

    private fun syncPending(confirmedRevision:Long?=null) {
        val settings = try { loadSettings() } catch (_: Exception) { null }
        if (settings == null) {
            status.text = I18n.t("Сначала сохраните настройки синхронизации")
            return
        }
        val client = try { VaultSyncClient(settings.endpoint, settings.token) } catch (_: Exception) {
            status.text = I18n.t("Нужен HTTPS endpoint или loopback-адрес")
            return
        }
        val masterPassword = masterPasswordInput.text.toString().toCharArray()
        if (!MasterPasswordSession.isValid(masterPassword)) {
                status.text = I18n.t("Введите мастер-пароль от 4 ASCII-символов для синхронизации")
            masterPassword.fill('\u0000')
            return
        }
        status.text = I18n.t("Синхронизация выполняется…\nОжидает синхронизации: {0}", queue.size())
        val localSnapshot=vault.snapshot()
        val portableSnapshot=vault.toBackupJson()
        SyncTaskRunner(syncExecutor).execute(task@{
            try {
                val snapshot = client.fetchSnapshot()
                latestSnapshot = snapshot
                val revision = snapshot.revision
                val preferences=getPreferences(MODE_PRIVATE)
                val dirty=preferences.getBoolean("local-changes",false)||queue.size()>0
                if(dirty&&snapshot.envelope!=null&&revision!=preferences.getLong("last-server-revision",-1)&&confirmedRevision!=revision){
                    runOnUiThread{if(AppSession.unlocked&&!isDestroyed)AlertDialog.Builder(this)
                        .setTitle(I18n.t("На сервере другая версия"))
                        .setMessage(I18n.t("Локальные записи сохранены. Отправить их вместо текущей серверной версии? Для обратного действия есть кнопка «Загрузить с сервера»."))
                        .setNegativeButton(I18n.t("Отмена")){_,_->masterPasswordInput.text.clear();status.text=I18n.t("Синхронизация отменена; локальные записи сохранены")}
                        .setPositiveButton(I18n.t("Отправить локальную")){_,_->syncPending(revision)}.show()}
                    return@task
                }
                if(dirty){
                    val remoteJson=snapshot.envelope?.let{PortableVaultCrypto.decrypt(it,masterPassword)}
                    val payload=SyncPayload.upload(LocalVault.fromPortableJson(portableSnapshot),remoteJson)
                    queue.enqueue("local-${System.currentTimeMillis()}",PortableVaultCrypto.pack(PortableVaultCrypto.encrypt(payload,masterPassword)))
                    persistQueue()
                }
                val result = if (queue.size() > 0) {
                    val coordinator = SyncCoordinator(queue) { change ->
                        val envelope = PortableVaultCrypto.unpack(change.ciphertext)
                        client.put(revision, envelope.version, envelope.salt, envelope.nonce, envelope.ciphertext)
                    }
                    coordinator.syncNext()
                } else if (snapshot.envelope != null) {
                    val remotePlaintext = PortableVaultCrypto.decrypt(snapshot.envelope, masterPassword)
                    val restoredVault = SyncPayload.received(remotePlaintext,vault)
                    vault = restoredVault
                    persistVault()
                    preferences.edit().putBoolean("local-changes",false).putLong("last-server-revision",revision).commit()
                    null
                } else null
                persistQueue()
                if(result==SyncResult.Saved){
                    preferences.edit().putLong("last-server-revision",revision+1).apply()
                    if(vault.snapshot()==localSnapshot)preferences.edit().putBoolean("local-changes",false).apply()
                }
                runOnUiThread {
                    masterPasswordInput.text.clear()
                    refreshAccounts()
                    notesPanel.refresh()
                    status.text = when (result) {
                        SyncResult.Saved -> I18n.t("Изменение синхронизировано (ревизия {0})", revision)
                        SyncResult.Conflict -> I18n.t("Конфликтов на сервере: {0}. Локальная версия сохранена", snapshot.conflicts.size)
                        is SyncResult.Failed -> I18n.t("Сервер недоступен ({0})", result.statusCode)
                        null -> if (snapshot.envelope != null && vault.accounts.isNotEmpty()) I18n.t("Серверная версия проверена или локальные данные сохранены") else I18n.t("Очередь синхронизации пуста")
                    } + I18n.t("\nОжидает синхронизации: {0}", queue.size())
                }
            } finally {
                masterPassword.fill('\u0000')
            }
        }) { error ->
            runOnUiThread {
                masterPasswordInput.text.clear()
                if (error != null) status.text = I18n.t("Синхронизация не выполнена: {0}", error.message ?: I18n.t("ошибка"))
            }
        }
    }

    override fun onDestroy() {
        lockHandler.removeCallbacks(autoLock)
        pendingBackupPassword?.fill('\u0000')
        syncExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val SETTINGS_KEY = "encrypted-sync-settings"
        private const val QUEUE_KEY = "pending-sync-queue"
        private const val VAULT_KEY = "encrypted-local-vault"
    }
}
