package local.accountvault.android.features.notes

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.widget.*
import local.accountvault.android.*
import local.accountvault.android.ui.MobileUi
import java.time.Instant

class NotesPanel(private val activity:Activity,private val vault:()->LocalVault,private val persist:()->Unit,private val report:(String)->Unit):LinearLayout(activity) {
    private class EditorDialog(activity:Activity):AlertDialog(activity){
        var onUserCancel:(()->Unit)?=null
        override fun cancel(){val action=onUserCancel;if(action==null)super.cancel()else action()}
    }
    private val search=EditText(activity).apply{hint="Поиск заметок";MobileUi.field(this);setSingleLine(true)}
    private val items=LinearLayout(activity).apply{orientation=VERTICAL}
    private val summary=label("",12f)
    private var editor:AlertDialog?=null
    init {
        orientation=VERTICAL
        val header=LinearLayout(activity).apply{gravity=android.view.Gravity.CENTER_VERTICAL}
        header.addView(label("Заметки",26f,true),LayoutParams(0,-2,1f))
        header.addView(Button(activity).apply{text="Новая заметка";MobileUi.button(this,true);textSize=12f;setOnClickListener{edit(null)}},LayoutParams(-2,-2))
        addView(header);addView(label("Идеи и планы — всегда под рукой.",14f))
        addView(search,params());addView(summary);addView(items)
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh()}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
    }
    private fun params()=LayoutParams(-1,-2).apply{bottomMargin=MobileUi.dp(this@NotesPanel,12)}
    private fun label(value:String,size:Float,bold:Boolean=false)=TextView(activity).apply{text=value;textSize=size;setTextColor(Color.parseColor(AndroidThemePolicy.mutedText));setPadding(0,MobileUi.dp(this,8),0,MobileUi.dp(this,10));if(bold)MobileUi.title(this)}
    fun refresh(){
        items.removeAllViews();val matches=vault().searchNotes(search.text.toString())
        summary.text=if(search.text.isNullOrBlank())"Всего: ${vault().notes.size} · закреплено: ${vault().notes.count{it.pinned}}" else "Найдено: ${matches.size} из ${vault().notes.size}"
        if(matches.isEmpty()){
            items.addView(MobileUi.section(activity,if(vault().notes.isEmpty())"Место для новой идеи" else "Заметки не найдены",if(vault().notes.isEmpty())"Сохраните мысль, список дел или ссылку на важный файл." else "Попробуйте другое название или фразу."));return
        }
        matches.forEach { note->
            val card=LinearLayout(activity).apply{orientation=VERTICAL;background=MobileUi.shape(this);val pad=MobileUi.dp(this,16);setPadding(pad,pad,pad,pad)}
            card.addView(label((if(note.pinned)"★  " else "")+note.title,18f,true).apply{setPadding(0,0,0,MobileUi.dp(this,8));setOnClickListener{edit(note)}})
            if(note.content.isNotBlank())card.addView(label(note.content,14f).apply{setPadding(0,0,0,MobileUi.dp(this,12));maxLines=5;ellipsize=android.text.TextUtils.TruncateAt.END;setLineSpacing(MobileUi.dp(this,3).toFloat(),1f);setOnClickListener{edit(note)}})
            val date=java.time.Instant.parse(note.updatedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM · HH:mm",java.util.Locale.forLanguageTag("ru")))
            card.addView(label(date,11f).apply{setPadding(0,0,0,MobileUi.dp(this,4))})
            note.links.forEach{link->
                card.addView(Button(activity).apply{text="${link.name}\n${link.provider}  ↗";contentDescription="Открыть файл: ${link.name}";MobileUi.button(this);textSize=13f;gravity=android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL;setOnClickListener{
                    try{link.validate();activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link.url.trim())).addCategory(Intent.CATEGORY_BROWSABLE))}
                    catch(_:Exception){report("Не удалось открыть файл. Проверьте ссылку и наличие браузера.")}
                }},params())
            }
            val actions=LinearLayout(activity)
            fun action(textValue:String,callback:()->Unit){actions.addView(Button(activity).apply{text=textValue;MobileUi.quiet(this);textSize=11f;minWidth=0;setPadding(0,0,0,0);setOnClickListener{callback()}},LayoutParams(0,-2,1f))}
            action("Изменить"){edit(note)}
            action(if(note.pinned)"Открепить" else "Закрепить"){change{NoteService(vault(),persist).save(note.copy(pinned=!note.pinned,updatedAt=Instant.now().toString()))}}
            action("Удалить"){AlertDialog.Builder(activity).setTitle("Удалить заметку?").setMessage(note.title).setNegativeButton("Отмена",null).setPositiveButton("Удалить"){_,_->change{NoteService(vault(),persist).remove(note.id)}}.show()}
            card.addView(actions);items.addView(card,params())
        }
    }
    private fun change(action:()->Unit){try{action();refresh();report("Заметки сохранены на устройстве")}catch(_:Exception){report("Не удалось сохранить заметку. Предыдущие данные остались.")}}
    private fun edit(note:VaultNote?){
        val name=EditText(activity).apply{hint="Название заметки";setText(note?.title?:"");setSingleLine(true);filters=arrayOf(android.text.InputFilter.LengthFilter(200));MobileUi.field(this)}
        val text=EditText(activity).apply{hint="Текст заметки";setText(note?.content?:"");inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=5;gravity=android.view.Gravity.TOP;filters=arrayOf(android.text.InputFilter.LengthFilter(500000));MobileUi.field(this)}
        val pinned=CheckBox(activity).apply{this.text="Закрепить заметку";isChecked=note?.pinned?:false;setTextColor(Color.parseColor(AndroidThemePolicy.text))}
        val linksEditor=NoteLinksEditor(activity,note?.links?:emptyList())
        val form=LinearLayout(activity).apply{orientation=VERTICAL;val pad=MobileUi.dp(this,16);setPadding(pad,pad,pad,pad);addView(name,params());addView(text,params());addView(pinned);addView(linksEditor,params())}
        val dialog=EditorDialog(activity).apply{
            setTitle(if(note==null)"Новая заметка" else "Изменить заметку")
            setView(ScrollView(activity).apply{addView(form)})
            setButton(AlertDialog.BUTTON_NEGATIVE,"Отмена"){_,_->}
            setButton(AlertDialog.BUTTON_POSITIVE,"Сохранить заметку"){_,_->}
        }
        editor=dialog
        fun dirty()=name.text.toString()!=(note?.title?:"")||text.text.toString()!=(note?.content?:"")||pinned.isChecked!=(note?.pinned?:false)||linksEditor.dirty()
        fun discard(){if(!dirty()){dialog.dismiss();return};AlertDialog.Builder(activity).setTitle("Закрыть без сохранения?").setNegativeButton("Продолжить редактирование",null).setPositiveButton("Закрыть"){_,_->dialog.dismiss()}.show()}
        dialog.onUserCancel={discard()}
        dialog.setOnShowListener{
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener{discard()}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(name.text.isNullOrBlank()){name.error="Введите название заметки";return@setOnClickListener}
                val links=try{linksEditor.read()}catch(_:IllegalArgumentException){return@setOnClickListener}
                try{NoteService(vault(),persist).save(VaultNote(note?.id?:java.util.UUID.randomUUID().toString(),name.text.toString().trim(),text.text.toString(),pinned.isChecked,links=links));dialog.dismiss();refresh();report("Заметка сохранена")}
                catch(_:Exception){name.error="Не удалось сохранить заметку"}
            }
        }
        dialog.setOnDismissListener{name.text.clear();text.text.clear();linksEditor.clearSensitive();if(editor===dialog)editor=null};dialog.show()
    }
    fun clearSensitive(){editor?.dismiss();items.removeAllViews();search.text.clear()}
}
