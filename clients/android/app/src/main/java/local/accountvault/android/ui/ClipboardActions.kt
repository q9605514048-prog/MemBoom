package local.accountvault.android.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import local.accountvault.android.ClipboardPolicy

object ClipboardActions {
    fun copy(activity: Activity, label: String, value: String) {
        if (value.isEmpty()) return
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        if (FieldPresentation.isSecret(label) && android.os.Build.VERSION.SDK_INT >= 33)
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        activity.window.decorView.postDelayed({
            val current = clipboard.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString()
            if (current == value) clipboard.setPrimaryClip(ClipData.newPlainText("MemBoom", ""))
        }, ClipboardPolicy.clearDelayMs)
        Toast.makeText(activity, I18n.t("Скопировано поле: {0}", label), Toast.LENGTH_SHORT).show()
    }
}
