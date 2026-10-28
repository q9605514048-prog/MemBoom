package local.accountvault.android.features.notes

import local.accountvault.android.ui.I18n

import android.app.Activity
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.widget.*
import local.accountvault.android.AndroidThemePolicy
import local.accountvault.android.ui.MobileUi

class NoteLinksEditor(private val activity:Activity,private val initial:List<NoteFileLink>):LinearLayout(activity) {
    private data class Row(val name:EditText,val url:EditText,val view:LinearLayout)
    private val rows=mutableListOf<Row>()
    private val list=LinearLayout(activity).apply{orientation=VERTICAL}
    private val error=TextView(activity).apply{setTextColor(Color.parseColor("#FFA8AF"));textSize=13f}
    private val add=Button(activity).apply{text=I18n.t("＋ Ссылка на файл");MobileUi.button(this);setOnClickListener{if(rows.size<NoteFileLink.MAX_LINKS)addRow(null)}}
    init {
        orientation=VERTICAL
        addView(TextView(activity).apply{text=I18n.t("Ссылки на файлы");textSize=18f;MobileUi.title(this);setPadding(0,16,0,12)})
        addView(TextView(activity).apply{text=I18n.t("Google Диск или Яндекс Диск. Файл откроется в браузере.");textSize=12f;setTextColor(Color.parseColor(AndroidThemePolicy.mutedText))})
        addView(list);addView(add,params());addView(error,params())
        initial.forEach{addRow(it)}
    }
    private fun params()=LayoutParams(-1,-2).apply{bottomMargin=MobileUi.dp(this@NoteLinksEditor,10)}
    private fun addRow(link:NoteFileLink?){
        val name=EditText(activity).apply{hint=I18n.t("Название файла");contentDescription=I18n.t("Название файла");setText(link?.name?:"");setSingleLine(true);filters=arrayOf(InputFilter.LengthFilter(160));MobileUi.field(this)}
        val url=EditText(activity).apply{hint=I18n.t("Ссылка на файл");contentDescription=I18n.t("Ссылка на файл");setText(link?.url?:"");setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI;filters=arrayOf(InputFilter.LengthFilter(4096));MobileUi.field(this)}
        val card=LinearLayout(activity).apply{orientation=VERTICAL;background=MobileUi.shape(this);val pad=MobileUi.dp(this,12);setPadding(pad,pad,pad,pad);addView(name,params());addView(url,params())}
        val row=Row(name,url,card)
        card.addView(Button(activity).apply{text=I18n.t("Убрать ссылку");MobileUi.quiet(this);setOnClickListener{rows.remove(row);list.removeView(card);this@NoteLinksEditor.error.text="";add.isEnabled=true}},params())
        rows.add(row);list.addView(card,params());add.isEnabled=rows.size<NoteFileLink.MAX_LINKS
    }
    private fun raw()=rows.map{NoteFileLink(it.name.text.toString(),it.url.text.toString())}.filter{it.name.isNotBlank()||it.url.isNotBlank()}
    fun read():List<NoteFileLink>{
        error.text=""
        val result=rows.mapNotNull{row->
            if(row.name.text.isNullOrBlank()&&row.url.text.isNullOrBlank())return@mapNotNull null
            if(row.name.text.isNullOrBlank()){row.name.error=I18n.t("Укажите название файла");throw IllegalArgumentException(I18n.t("Укажите название файла"))}
            try{NoteFileLink(row.name.text.toString(),row.url.text.toString()).normalized()}
            catch(e:IllegalArgumentException){row.url.error=e.message;throw e}
        }
        try{NoteFileLink.validateList(result)}catch(e:IllegalArgumentException){error.text=e.message;throw e}
        return result
    }
    fun dirty()=raw()!=initial
    fun clearSensitive(){rows.forEach{it.name.text.clear();it.url.text.clear()};rows.clear();list.removeAllViews()}
}
