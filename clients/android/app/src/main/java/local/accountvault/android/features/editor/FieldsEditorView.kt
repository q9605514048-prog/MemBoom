package local.accountvault.android.features.editor

import local.accountvault.android.ui.I18n

import android.content.Context
import android.graphics.Color
import android.widget.*
import local.accountvault.android.AndroidThemePolicy
import local.accountvault.android.VaultField
import local.accountvault.android.ui.MobileUi

class FieldsEditorView(context: Context,initial:List<VaultField> = emptyList()):LinearLayout(context) {
    private data class Row(val container:LinearLayout,val label:EditText,val value:EditText)
    private val rows=mutableListOf<Row>()
    private val holder=LinearLayout(context).apply{orientation=VERTICAL}
    init {
        orientation=VERTICAL
        addView(holder)
        addView(Button(context).apply{text=I18n.t("Добавить поле");MobileUi.button(this);setOnClickListener{append(VaultField("",""))}},LayoutParams(-1,-2))
        initial.forEach(::append)
    }
    private fun append(field:VaultField){
        fun input(h:String,v:String)=EditText(context).apply{hint=h;setText(v);MobileUi.field(this);layoutParams=LayoutParams(-1,-2).apply{bottomMargin=MobileUi.dp(this@FieldsEditorView,8)}}
        val label=input(I18n.t("Название поля"),field.label)
        val value=input(I18n.t("Значение"),field.value)
        val row=LinearLayout(context).apply{orientation=VERTICAL;val pad=MobileUi.dp(this,10);setPadding(pad,pad,pad,pad);background=MobileUi.shape(this)}
        val entry=Row(row,label,value)
        val secret=listOf("парол","password","secret","token","ключ","key").any{field.label.lowercase().contains(it)}
        if(secret){value.inputType=129;row.addView(CheckBox(context).apply{text=I18n.t("Показать значение");setTextColor(Color.parseColor(AndroidThemePolicy.mutedText));setOnCheckedChangeListener{_,checked->value.inputType=if(checked)145 else 129}})}
        row.addView(label);row.addView(value)
        row.addView(Button(context).apply{text=I18n.t("Удалить поле");MobileUi.quiet(this);setTextColor(Color.parseColor("#EA8D96"));setOnClickListener{rows.remove(entry);holder.removeView(row)}},LayoutParams(-1,-2))
        rows.add(entry);holder.addView(row,LayoutParams(-1,-2).apply{bottomMargin=MobileUi.dp(this@FieldsEditorView,10)})
    }
    fun readFields():List<VaultField> = rows.map{
        if(it.label.text.isBlank()){it.label.error=I18n.t("Укажите название поля");throw IllegalArgumentException("Field label required")}
        VaultField(it.label.text.toString().trim(),it.value.text.toString())
    }
    fun clearFields(){rows.clear();holder.removeAllViews()}
}
