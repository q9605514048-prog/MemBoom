package local.accountvault.android.features.editor

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import local.accountvault.android.VaultField
import local.accountvault.android.ui.*

class FieldsEditorView(context: Context, private val initial: List<VaultField> = emptyList()) : LinearLayout(context) {
    private data class Row(val container: LinearLayout, val label: EditText, val value: EditText)
    private val rows = mutableListOf<Row>()
    private val holder = MobileUi.stack(context)
    init {
        orientation = VERTICAL
        addView(holder)
        addView(MobileUi.action(context, I18n.t("Добавить поле")) { append(VaultField("", "")) }, LayoutParams(-1, -2))
        initial.forEach(::append)
    }
    private fun append(field: VaultField) {
        val label = EditText(context).apply { hint = I18n.t("Название поля"); setText(field.label); setSingleLine(true); MobileUi.field(this) }
        val value = EditText(context).apply { hint = I18n.t("Значение"); setText(field.value); MobileUi.field(this) }
        val row = MobileUi.stack(context, 16).apply { background = MobileUi.shape(this) }
        val entry = Row(row, label, value)
        val reveal = CheckBox(context).apply {
            text = I18n.t("Показать значение"); MobileUi.check(this)
            setOnCheckedChangeListener { _, visible ->
                value.transformationMethod = if (visible) null else android.text.method.PasswordTransformationMethod.getInstance()
                value.typeface = Typeface.MONOSPACE
            }
        }
        val generator = MobileUi.action(context, I18n.t("Генератор паролей")) { (context as? Activity)?.let { PasswordControls.generator(it, value) } }
        fun updateSecret() {
            val secret = FieldPresentation.isSecret(label.text.toString())
            reveal.isChecked = false
            reveal.visibility = if (secret) VISIBLE else GONE
            generator.visibility = if (secret) VISIBLE else GONE
            value.inputType = if (secret) 129 else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            value.transformationMethod = if (secret) android.text.method.PasswordTransformationMethod.getInstance() else null
            value.typeface = if (secret) Typeface.MONOSPACE else Typeface.create("sans-serif", Typeface.NORMAL)
        }
        label.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSecret() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateSecret()
        row.addView(MobileUi.label(context, I18n.t("Название поля"), label)); row.addView(label, MobileUi.gapParams(row, 8))
        row.addView(MobileUi.label(context, I18n.t("Значение"), value)); row.addView(value, MobileUi.gapParams(row, 8))
        row.addView(reveal)
        row.addView(MobileUi.action(context, I18n.t("Копировать")) { (context as? Activity)?.let { ClipboardActions.copy(it, label.text.toString(), value.text.toString()) } }, LayoutParams(-1,-2))
        row.addView(generator, LayoutParams(-1, -2))
        row.addView(MobileUi.action(context, I18n.t("Удалить поле")) { rows.remove(entry); holder.removeView(row); label.text.clear(); value.text.clear() }.apply { MobileUi.danger(this) }, LayoutParams(-1, -2))
        rows.add(entry); holder.addView(row, MobileUi.gapParams(row, 16))
        if (field.label.isEmpty()) label.requestFocus()
    }
    private fun raw() = rows.map { VaultField(it.label.text.toString(), it.value.text.toString()) }
    fun dirty() = raw() != initial
    fun readFields(): List<VaultField> = rows.map {
        if (it.label.text.isBlank()) { it.label.error = I18n.t("Укажите название поля"); throw IllegalArgumentException("Field label required") }
        VaultField(it.label.text.toString().trim(), it.value.text.toString())
    }
    fun clearFields() { rows.forEach { it.label.text.clear(); it.value.text.clear() }; rows.clear(); holder.removeAllViews() }
}
