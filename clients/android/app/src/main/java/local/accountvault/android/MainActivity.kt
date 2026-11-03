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
import android.widget.Toast
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
import local.accountvault.android.ui.*
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
    private var accountFilter=AccountFilter()
    private lateinit var filterButton:Button
    private lateinit var syncSummary:TextView
    private val openScreens=mutableSetOf<ScreenDialog>()
    private var pendingBackupJson: String? = null
    private var pendingBackupPassword: CharArray? = null
    private val lockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoLock = Runnable { lockVault() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountFilter=AccountFilter(savedInstanceState?.getString("filter-category"),savedInstanceState?.getBoolean("filter-favorites")?:false,
            AccountSort.entries.getOrElse(savedInstanceState?.getInt("filter-sort")?:0){AccountSort.NAME})
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
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
            maxLines=4
            background=MobileUi.shape(this)
            setPadding(dp(12),dp(8),dp(12),dp(8))
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
                    if(category.isEmpty()){categoryInput.error=I18n.t("Категория и название обязательны");categoryInput.requestFocus()}else{accountInput.error=I18n.t("Категория и название обязательны");accountInput.requestFocus()}
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
        filterButton=MobileUi.action(this,I18n.t("Фильтры и сортировка")){showAccountFilters()}
        val accountsPage = pageContent().apply {
            addView(heading(I18n.t("Ваши аккаунты"),26f))
            addView(caption(I18n.t("Логины, пароли и всё, что важно сохранить.")))
            addView(searchInput)
            addView(filterButton,MobileUi.gapParams(this,4))
            addView(accountsSummary)
            addView(accountsLayout)
        }
        val showPassword=PasswordControls.reveal(this,passwordInput)
        val generatePassword=MobileUi.action(this,I18n.t("Генератор паролей")){PasswordControls.generator(this,passwordInput)}
        val copyPassword=MobileUi.action(this,I18n.t("Копировать пароль")){copyValue(I18n.t("Пароль"),passwordInput.text.toString())}
        customFields=FieldsEditorView(this)
        val addPage = pageContent().apply {
            addView(heading(I18n.t("Новый аккаунт"),26f))
            addView(caption(I18n.t("Запись будет сохранена на этом телефоне.")))
            val extra=Button(this@MainActivity).apply{text=I18n.t("Дополнительные поля  ＋");MobileUi.quiet(this);setOnClickListener{customFields.visibility=if(customFields.visibility==View.GONE)View.VISIBLE else View.GONE;text=if(customFields.visibility==View.VISIBLE)I18n.t("Дополнительные поля  −") else I18n.t("Дополнительные поля  ＋")}}
            customFields.visibility=View.GONE
            addView(MobileUi.section(this@MainActivity,I18n.t("Основное"),"",caption(I18n.t("Категория")),categoryInput,caption(I18n.t("Название")),accountInput))
            addView(MobileUi.section(this@MainActivity,I18n.t("Данные для входа"),"",caption(I18n.t("Почта / логин")),emailInput,caption(I18n.t("Пароль")),passwordInput,showPassword,copyPassword,generatePassword,extra,customFields))
            addView(addButton,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(16)})
        }
        syncSummary=caption("")
        val settingsPage = pageContent().apply {
            addView(heading(I18n.t("Настройки"),28f))
            addView(caption(I18n.t("Ваш аккаунт, резервные копии и защита.")))
            val accountButton=MobileUi.action(this@MainActivity,I18n.t("Управление аккаунтом")){accountScreenActive=true;startActivity(Intent(this@MainActivity,AccountActivity::class.java))}
            val loginLabel=try{AccountAuthStore.load(this@MainActivity)?.login}catch(_:Exception){null}
            addView(MobileUi.section(this@MainActivity,I18n.t("Хранилище"),loginLabel?.let{I18n.t("Вы вошли как {0}", it)}?:I18n.t("Локальный режим — записи доступны без интернета."),accountButton))
            val exportButton=MobileUi.action(this@MainActivity,I18n.t("Создать резервную копию"),true){promptBackup(true)}
            val importButton=MobileUi.action(this@MainActivity,I18n.t("Импорт резервной копии")){promptBackup(false)}
            addView(MobileUi.section(this@MainActivity,I18n.t("Резервные копии"),I18n.t("Зашифрованный файл для переноса и восстановления. Импорт добавляет записи, сохраняя существующие."),exportButton,importButton))
            val downloadButton=MobileUi.action(this@MainActivity,I18n.t("Загрузить с сервера")){downloadServerVersion()}
            addView(MobileUi.section(this@MainActivity,I18n.t("Синхронизация"),I18n.t("Ручной обмен с MemBoom API. Используйте мастер-пароль серверного хранилища."),syncSummary,MobileUi.label(this@MainActivity,I18n.t("Мастер-пароль для синхронизации"),masterPasswordInput),masterPasswordInput,syncButton,downloadButton))
            val api=MobileUi.section(this@MainActivity,I18n.t("Подключение API"),I18n.t("Адрес, защищённый токен и регистрация этого телефона."),MobileUi.label(this@MainActivity,I18n.t("Адрес сервера"),endpointInput),endpointInput,MobileUi.label(this@MainActivity,I18n.t("Токен устройства"),tokenInput),tokenInput,registerDeviceButton,saveSettingsButton)
            api.visibility=View.GONE
            addView(MobileUi.action(this@MainActivity,I18n.t("Подключение API")){api.visibility=if(api.visibility==View.GONE)View.VISIBLE else View.GONE},MobileUi.gapParams(this))
            addView(api)
            val conflicts=MobileUi.section(this@MainActivity,I18n.t("Конфликты версий"),I18n.t("Выберите версию только после проверки конфликта."),MobileUi.label(this@MainActivity,I18n.t("ID конфликта для разрешения"),conflictIdInput),conflictIdInput,keepLocalButton,acceptServerButton)
            conflicts.visibility=View.GONE
            addView(MobileUi.action(this@MainActivity,I18n.t("Конфликты версий")){conflicts.visibility=if(conflicts.visibility==View.GONE)View.VISIBLE else View.GONE},MobileUi.gapParams(this))
            addView(conflicts)
            addView(MobileUi.section(this@MainActivity,I18n.t("Интерфейс"),"",I18n.languageButton(this@MainActivity)))
            addView(MobileUi.section(this@MainActivity,I18n.t("Безопасность"),I18n.t("Хранилище блокируется при выходе и после 5 минут бездействия."),MobileUi.action(this@MainActivity,I18n.t("Заблокировать хранилище")){lockVault()}))
            addView(MobileUi.section(this@MainActivity,I18n.t("О приложении"),"MemBoom ${packageManager.getPackageInfo(packageName,0).versionName} • Android",I18n.termsButton(this@MainActivity)))
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
            addView(MobileUi.brand(this@MainActivity))
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
        closeSensitiveScreens()
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
        if(!isChangingConfigurations&&!backupPickerActive&&!accountScreenActive){AppSession.unlocked=false;closeSensitiveScreens();vault=LocalVault();if(::notesPanel.isInitialized)notesPanel.clearSensitive()}
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
        outState.putString("filter-category",accountFilter.category)
        outState.putBoolean("filter-favorites",accountFilter.favoritesOnly)
        outState.putInt("filter-sort",accountFilter.sort.ordinal)
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
        text = value; textSize = 14f
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
        if(index==3)refreshSyncSummary()
        pages[index].apply{animate().cancel();alpha=1f;translationY=0f;if(android.provider.Settings.Global.getFloat(contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f){alpha=0f;translationY=dp(8).toFloat();animate().alpha(1f).translationY(0f).setDuration(AndroidThemePolicy.motionMs).start()}}
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
        val matches=accountFilter.apply(vault,searchInput.text.toString())
        accountsSummary.text=I18n.t("Найдено: {0} из {1}",matches.size,vault.accounts.size)
        filterButton.text=listOfNotNull(I18n.t("Фильтры и сортировка"),accountFilter.category,if(accountFilter.favoritesOnly)I18n.t("Избранное") else null).joinToString(" · ")
        if(matches.isEmpty()){
            val reset=MobileUi.action(this,if(vault.accounts.isEmpty())I18n.t("Добавить первый аккаунт") else I18n.t("Сбросить фильтры"),true){
                if(vault.accounts.isEmpty())showPage(1)else{accountFilter=AccountFilter();searchInput.text.clear();refreshAccounts()}
            }
            accountsLayout.addView(MobileUi.section(this,if(vault.accounts.isEmpty())I18n.t("Начните с первого аккаунта")else I18n.t("Ничего не найдено"),if(vault.accounts.isEmpty())I18n.t("Добавьте запись — она останется доступна без интернета.")else I18n.t("Попробуйте другое название, категорию или логин."),reset))
        }
        matches.forEach{account->
            val card=MobileUi.stack(this,16)
            card.addView(caption(account.category).apply{setTextColor(Color.parseColor(AndroidThemePolicy.magicGold));textSize=13f;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
            val row=LinearLayout(this).apply{gravity=android.view.Gravity.CENTER_VERTICAL}
            row.addView(heading(account.name,18f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f))
            row.addView(MobileUi.text(this,if(account.favorite)"★  ›" else "›",22f).apply{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO;setTextColor(Color.parseColor(AndroidThemePolicy.magicGold))})
            card.addView(row)
            FieldPresentation.loginPreview(account)?.let{card.addView(caption(it).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})}
            card.contentDescription=I18n.t("Открыть аккаунт {0} / {1}",account.category,account.name)+(if(account.favorite)" · "+I18n.t("Избранное") else "")
            MobileUi.cardClick(card){showAccountDetails(account)}
            accountsLayout.addView(card,MobileUi.gapParams(card))
        }
    }

    private fun showAccountFilters(){
        val screen=ScreenDialog(this,I18n.t("Фильтры и сортировка"))
        val categories=listOf(I18n.t("Все категории"))+vault.accounts.map{it.category}.distinct().sorted()
        val category=android.widget.Spinner(this).apply{minimumHeight=dp(56);adapter=android.widget.ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,categories);setSelection(accountFilter.category?.let{categories.indexOf(it).coerceAtLeast(0)}?:0);contentDescription=I18n.t("Категория")}
        val favorite=CheckBox(this).apply{text=I18n.t("Только избранное");isChecked=accountFilter.favoritesOnly;MobileUi.check(this)}
        val order=android.widget.RadioGroup(this)
        listOf(I18n.t("По названию"),I18n.t("По категории"),I18n.t("Сначала избранное")).forEachIndexed{index,label->order.addView(android.widget.RadioButton(this).apply{id=View.generateViewId();text=label;MobileUi.check(this);isChecked=index==accountFilter.sort.ordinal})}
        screen.body.addView(heading(I18n.t("Категория")));screen.body.addView(category);screen.body.addView(favorite)
        screen.body.addView(heading(I18n.t("Сортировка")));screen.body.addView(order)
        screen.action(I18n.t("Применить"),true){accountFilter=AccountFilter(if(category.selectedItemPosition==0)null else categories[category.selectedItemPosition],favorite.isChecked,AccountSort.entries[order.indexOfChild(order.findViewById(order.checkedRadioButtonId)).coerceAtLeast(0)]);refreshAccounts();screen.dismiss()}
        screen.action(I18n.t("Сбросить фильтры")){accountFilter=AccountFilter();refreshAccounts();screen.dismiss()}
        trackScreen(screen)
    }

    private fun trackScreen(screen:ScreenDialog){openScreens.add(screen);screen.setOnDismissListener{openScreens.remove(screen)};screen.show()}
    private fun closeSensitiveScreens(){
        openScreens.toList().forEach{it.dismiss()};openScreens.clear()
        if(::passwordInput.isInitialized){passwordInput.text.clear();emailInput.text.clear();accountInput.text.clear();masterPasswordInput.text.clear();tokenInput.text.clear();customFields.clearFields()}
        if(::accountsLayout.isInitialized)accountsLayout.removeAllViews()
    }

    private fun copyValue(label:String,value:String){
        ClipboardActions.copy(this,label,value)
        if(value.isNotEmpty())status.text=I18n.t("Скопировано поле: {0}",label)
    }

    private fun showAccountDetails(account:VaultAccount){
        val screen=ScreenDialog(this,I18n.t("Аккаунт"))
        screen.body.addView(caption(account.category).apply{setTextColor(Color.parseColor(AndroidThemePolicy.magicGold))})
        screen.body.addView(heading(account.name,28f))
        screen.body.addView(caption(I18n.t("Секретные значения скрыты. Показывайте их только при необходимости.")))
        account.fields.forEach{field->
            val disclosure=FieldDisclosure(field)
            val value=MobileUi.text(this,if(field.value.isEmpty())I18n.t("Не заполнено")else disclosure.displayValue,16f,true).apply{
                if(disclosure.secret)typeface=android.graphics.Typeface.MONOSPACE
                setTextIsSelectable(true);setPadding(0,dp(8),0,dp(12))
            }
            val copy=MobileUi.action(this,I18n.t("Копировать")){copyValue(field.label,field.value)}.apply{isEnabled=field.value.isNotEmpty();contentDescription=I18n.t("Копировать {0} аккаунта {1}",field.label,account.name)}
            val fieldCard=MobileUi.section(this,field.label,"",value,copy)
            if(disclosure.secret)fieldCard.addView(MobileUi.action(this,I18n.t("Показать значение")){
                disclosure.toggle();value.text=disclosure.displayValue
                val button=fieldCard.getChildAt(fieldCard.childCount-1) as Button
                button.text=if(disclosure.revealed)I18n.t("Скрыть значение")else I18n.t("Показать значение")
            }.apply{isEnabled=field.value.isNotEmpty()},LinearLayout.LayoutParams(-1,-2))
            screen.body.addView(fieldCard)
        }
        val favorite=MobileUi.action(this,if(account.favorite)I18n.t("Убрать из избранного")else I18n.t("В избранное")){
            try{AccountFavoriteAction.toggle(vault,account.id,::persistVault);enqueueCurrentSnapshot();refreshAccounts();screen.dismiss();vault.accounts.firstOrNull{it.id==account.id}?.let(::showAccountDetails)}
            catch(_:Exception){Toast.makeText(this,I18n.t("Не удалось сохранить изменения"),Toast.LENGTH_SHORT).show()}
        }
        screen.body.addView(MobileUi.section(this,I18n.t("Действия"),"",favorite,MobileUi.action(this,I18n.t("Удалить аккаунт")){confirmDeleteAccount(account,screen)}.apply{MobileUi.danger(this)}))
        screen.action(I18n.t("Изменить аккаунт"),true){screen.dismiss();showEditAccount(account)}
        trackScreen(screen)
    }

    private fun confirmDeleteAccount(account:VaultAccount,screen:ScreenDialog){
        AlertDialog.Builder(this).setTitle(I18n.t("Удалить аккаунт?")).setMessage("${account.category} / ${account.name}")
            .setNegativeButton(I18n.t("Отмена"),null).setPositiveButton(I18n.t("Удалить")){_,_->
                try{AccountEntryService(vault,::persistVault).remove(account.id);enqueueCurrentSnapshot();refreshAccounts();screen.dismiss();status.text=I18n.t("Аккаунт удалён локально")}
                catch(_:Exception){Toast.makeText(this,I18n.t("Не удалось удалить запись. Данные сохранены."),Toast.LENGTH_LONG).show()}
            }.show().getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor(AndroidThemePolicy.danger))
    }

    private fun showEditAccount(account: VaultAccount) {
        val category=EditText(this).apply{hint=I18n.t("Категория");setSingleLine(true);setText(account.category);applyMeguminFieldStyle()}
        val name=EditText(this).apply{hint=I18n.t("Название аккаунта");setSingleLine(true);setText(account.name);applyMeguminFieldStyle()}
        val fieldsEditor=FieldsEditorView(this,account.fields)
        val screen=ScreenDialog(this,I18n.t("Изменить аккаунт"))
        screen.body.addView(MobileUi.section(this,I18n.t("Основное"),"",MobileUi.label(this,I18n.t("Категория"),category),category,MobileUi.label(this,I18n.t("Название"),name),name))
        screen.body.addView(heading(I18n.t("Поля аккаунта")))
        screen.body.addView(fieldsEditor)
        screen.onBack={
            if(category.text.toString()!=account.category||name.text.toString()!=account.name||fieldsEditor.dirty())AlertDialog.Builder(this).setTitle(I18n.t("Закрыть без сохранения?")).setNegativeButton(I18n.t("Продолжить редактирование"),null).setPositiveButton(I18n.t("Закрыть")){_,_->screen.dismiss()}.show()else screen.dismiss()
        }
        screen.action(I18n.t("Сохранить"),true){
            if(category.text.isBlank()||name.text.isBlank()){name.error=I18n.t("Категория и название обязательны");return@action}
            try{AccountEntryService(vault,::persistVault).update(account.id,category.text.toString(),name.text.toString(),fieldsEditor.readFields())}
            catch(_:Exception){name.error=I18n.t("Не удалось сохранить. Проверьте названия полей.");return@action}
            enqueueCurrentSnapshot();refreshAccounts();status.text=I18n.t("Аккаунт изменён локально");screen.dismiss()
            vault.accounts.firstOrNull{it.id==account.id}?.let(::showAccountDetails)
        }
        trackScreen(screen)
    }

    private fun refreshSyncSummary(){
        val dirty=getPreferences(MODE_PRIVATE).getBoolean("local-changes",false)
        syncSummary.text=when{
            queue.size()>0->I18n.t("Ожидает синхронизации: {0}",queue.size())
            dirty->I18n.t("Есть локальные изменения. Запустите синхронизацию вручную.")
            else->I18n.t("Состояние сервера проверяется при ручной синхронизации.")
        }
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
        openScreens.toList().forEach{it.dismiss()}
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
