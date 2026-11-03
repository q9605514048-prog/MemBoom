package local.accountvault.android.features.notes

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.widget.*
import local.accountvault.android.*
import local.accountvault.android.ui.*
import java.time.Instant

class NotesPanel(private val activity:Activity,private val vault:()->LocalVault,private val persist:()->Unit,private val report:(String)->Unit):LinearLayout(activity) {
    private val search=EditText(activity).apply{hint=I18n.t("Поиск заметок");MobileUi.field(this);setSingleLine(true)}
    private val items=MobileUi.stack(activity)
    private val summary=label("",14f)
    private val screens=mutableSetOf<ScreenDialog>()
    private var sortByTitle=false
    private var pinnedOnly=false
    private val order=MobileUi.action(activity,I18n.t("Сначала закреплённые")){showSort()}
    init {
        orientation=VERTICAL
        addView(label(I18n.t("Заметки"),28f,true))
        addView(label(I18n.t("Идеи и планы — всегда под рукой."),14f))
        addView(MobileUi.action(activity,I18n.t("Новая заметка"),true){edit(null)},params())
        addView(search,params());addView(order,params());addView(summary);addView(items)
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh()}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
    }
    private fun params()=MobileUi.gapParams(this)
    private fun label(value:String,size:Float=14f,bold:Boolean=false)=MobileUi.text(activity,value,size,bold).apply{setPadding(0,MobileUi.dp(this,8),0,MobileUi.dp(this,12))}
    private fun date(note:VaultNote)=Instant.parse(note.updatedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM · HH:mm",java.util.Locale.forLanguageTag(I18n.localeTag)))
    private fun track(screen:ScreenDialog){screens.add(screen);screen.setOnDismissListener{screens.remove(screen)};screen.show()}
    private fun showSort(){
        val screen=ScreenDialog(activity,I18n.t("Фильтры и сортировка"))
        val pinned=CheckBox(activity).apply{text=I18n.t("Только закреплённые");isChecked=pinnedOnly;MobileUi.check(this)}
        val title=CheckBox(activity).apply{text=I18n.t("По названию");isChecked=sortByTitle;MobileUi.check(this)}
        screen.body.addView(label(I18n.t("Закреплённые заметки всегда идут первыми. Остальные — по дате изменения или названию.")))
        screen.body.addView(pinned);screen.body.addView(title)
        screen.action(I18n.t("Применить"),true){pinnedOnly=pinned.isChecked;sortByTitle=title.isChecked;refresh();screen.dismiss()}
        track(screen)
    }
    fun refresh(){
        items.removeAllViews()
        val matches=NoteListPresentation.select(vault().searchNotes(search.text.toString()),pinnedOnly,sortByTitle)
        order.text=I18n.t("Фильтры и сортировка")+(if(pinnedOnly)" · "+I18n.t("Закреплённые")else "")
        summary.text=I18n.t("Всего: {0} · закреплено: {1}",vault().notes.size,vault().notes.count{it.pinned})+" · "+I18n.t("Найдено: {0} из {1}",matches.size,vault().notes.size)
        if(matches.isEmpty()){
            items.addView(MobileUi.section(activity,if(vault().notes.isEmpty())I18n.t("Место для новой идеи") else I18n.t("Заметки не найдены"),if(vault().notes.isEmpty())I18n.t("Сохраните мысль, список дел или ссылку на важный файл.")else I18n.t("Попробуйте другое название или фразу."),MobileUi.action(activity,I18n.t("Сбросить фильтры")){pinnedOnly=false;sortByTitle=false;search.text.clear();refresh()}));return
        }
        matches.forEach{note->
            val card=MobileUi.stack(activity,16)
            if(note.pinned)card.addView(label(I18n.t("★ Закреплено"),13f).apply{setTextColor(Color.parseColor(AndroidThemePolicy.magicGold))})
            card.addView(label(note.title,18f,true).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
            if(note.content.isNotBlank())card.addView(label(note.content).apply{maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END})
            card.addView(label(date(note)+(if(note.links.isNotEmpty())" · "+I18n.t("Файлов: {0}",note.links.size)else ""),13f))
            MobileUi.cardClick(card){details(note)}
            card.contentDescription=I18n.t("Открыть заметку: {0}",note.title)
            items.addView(card,params())
        }
    }
    private fun change(action:()->Unit):Boolean=try{action();refresh();report(I18n.t("Заметки сохранены на устройстве"));true}catch(_:Exception){Toast.makeText(activity,I18n.t("Не удалось сохранить заметку. Предыдущие данные остались."),Toast.LENGTH_LONG).show();false}
    private fun details(note:VaultNote){
        val screen=ScreenDialog(activity,I18n.t("Заметка"))
        screen.body.addView(label(note.title,28f,true));screen.body.addView(label(date(note),13f))
        screen.body.addView(label(note.content,16f,true).apply{setTextIsSelectable(true);setLineSpacing(MobileUi.dp(this,6).toFloat(),1f)})
        if(note.links.isNotEmpty()){
            screen.body.addView(label(I18n.t("Ссылки на файлы"),19f,true))
            note.links.forEach{link->screen.body.addView(MobileUi.action(activity,"${link.name}  ↗\n${link.provider}"){
                try{link.validate();activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link.url.trim())).addCategory(Intent.CATEGORY_BROWSABLE))}
                catch(_:Exception){Toast.makeText(activity,I18n.t("Не удалось открыть файл. Проверьте ссылку и наличие браузера."),Toast.LENGTH_LONG).show()}
            }.apply{contentDescription=I18n.t("Открыть файл: {0}",link.name);setTextColor(Color.parseColor(AndroidThemePolicy.magicGold))},params())}
        }
        screen.body.addView(MobileUi.action(activity,if(note.pinned)I18n.t("Открепить")else I18n.t("Закрепить")){
            if(change{NoteService(vault(),persist).save(note.copy(pinned=!note.pinned,updatedAt=Instant.now().toString()))}){screen.dismiss();vault().notes.firstOrNull{it.id==note.id}?.let(::details)}
        },params())
        screen.body.addView(MobileUi.action(activity,I18n.t("Удалить заметку")){
            AlertDialog.Builder(activity).setTitle(I18n.t("Удалить заметку?")).setMessage(note.title).setNegativeButton(I18n.t("Отмена"),null).setPositiveButton(I18n.t("Удалить")){_,_->if(change{NoteService(vault(),persist).remove(note.id)})screen.dismiss()}.show().getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor(AndroidThemePolicy.danger))
        }.apply{MobileUi.danger(this)},params())
        screen.action(I18n.t("Изменить заметку"),true){screen.dismiss();edit(note)}
        track(screen)
    }
    private fun edit(note:VaultNote?){
        val name=EditText(activity).apply{hint=I18n.t("Название заметки");setText(note?.title?:"");setSingleLine(true);filters=arrayOf(android.text.InputFilter.LengthFilter(200));MobileUi.field(this)}
        val text=EditText(activity).apply{hint=I18n.t("Текст заметки");setText(note?.content?:"");inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=10;gravity=android.view.Gravity.TOP;filters=arrayOf(android.text.InputFilter.LengthFilter(500000));MobileUi.field(this)}
        val pinned=CheckBox(activity).apply{this.text=I18n.t("Закрепить заметку");isChecked=note?.pinned?:false;MobileUi.check(this)}
        val linksEditor=NoteLinksEditor(activity,note?.links?:emptyList())
        val screen=ScreenDialog(activity,if(note==null)I18n.t("Новая заметка")else I18n.t("Изменить заметку"))
        screen.body.addView(MobileUi.label(activity,I18n.t("Название заметки"),name));screen.body.addView(name,params())
        screen.body.addView(MobileUi.label(activity,I18n.t("Текст заметки"),text));screen.body.addView(text,params());screen.body.addView(pinned);screen.body.addView(linksEditor,params())
        fun dirty()=name.text.toString()!=(note?.title?:"")||text.text.toString()!=(note?.content?:"")||pinned.isChecked!=(note?.pinned?:false)||linksEditor.dirty()
        screen.onBack={if(!dirty())screen.dismiss()else AlertDialog.Builder(activity).setTitle(I18n.t("Закрыть без сохранения?")).setNegativeButton(I18n.t("Продолжить редактирование"),null).setPositiveButton(I18n.t("Закрыть")){_,_->screen.dismiss()}.show()}
        screen.action(I18n.t("Сохранить заметку"),true){
            if(name.text.isNullOrBlank()){name.error=I18n.t("Введите название заметки");return@action}
            val links=try{linksEditor.read()}catch(_:IllegalArgumentException){return@action}
            try{NoteService(vault(),persist).save(VaultNote(note?.id?:java.util.UUID.randomUUID().toString(),name.text.toString().trim(),text.text.toString(),pinned.isChecked,links=links));screen.dismiss();refresh();report(I18n.t("Заметка сохранена"))}
            catch(_:Exception){name.error=I18n.t("Не удалось сохранить заметку")}
        }
        screens.add(screen);screen.setOnDismissListener{name.text.clear();text.text.clear();linksEditor.clearSensitive();screens.remove(screen)};screen.show()
    }
    fun clearSensitive(){screens.toList().forEach{it.dismiss()};screens.clear();items.removeAllViews();search.text.clear()}
}
