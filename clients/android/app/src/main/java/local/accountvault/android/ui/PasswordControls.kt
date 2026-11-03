package local.accountvault.android.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.widget.*
import local.accountvault.android.features.security.PasswordGenerator

object PasswordControls {
    fun reveal(activity: Activity, field: EditText, title: String = I18n.t("Показать пароль")) = CheckBox(activity).apply {
        text = title; MobileUi.check(this)
        setOnCheckedChangeListener { _, visible ->
            field.transformationMethod = if (visible) null else android.text.method.PasswordTransformationMethod.getInstance()
            field.typeface = Typeface.MONOSPACE
            field.setSelection(field.text.length)
        }
    }
    fun generator(activity: Activity, target: EditText) {
        val body = MobileUi.stack(activity, 20)
        val preview = EditText(activity).apply { inputType = 129; setSingleLine(true); MobileUi.field(this); typeface = Typeface.MONOSPACE; setText(PasswordGenerator.generate()) }
        val length = TextView(activity)
        val slider = SeekBar(activity).apply { min = 4; max = 128; progress = 20; minimumHeight = MobileUi.dp(this, 48); contentDescription = I18n.t("Длина пароля") }
        fun generate() { preview.setText(PasswordGenerator.generate(slider.progress)); length.text = I18n.t("Длина пароля: {0}", slider.progress) }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) generate() }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        body.addView(MobileUi.text(activity, I18n.t("Буквы, цифры и спецсимволы. Длина от 4 до 128 символов.")))
        body.addView(length); body.addView(slider); body.addView(preview)
        body.addView(reveal(activity, preview)); body.addView(MobileUi.action(activity, I18n.t("Создать другой пароль")) { generate() })
        generate()
        val dialog = AlertDialog.Builder(activity).setTitle(I18n.t("Генератор паролей")).setView(body)
            .setNegativeButton(I18n.t("Отмена"), null).setPositiveButton(I18n.t("Использовать пароль")) { _, _ -> target.setText(preview.text); target.setSelection(target.text.length) }.create()
        dialog.setOnDismissListener { preview.text.clear() }
        dialog.window?.let { if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) it.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        dialog.show()
    }
}
