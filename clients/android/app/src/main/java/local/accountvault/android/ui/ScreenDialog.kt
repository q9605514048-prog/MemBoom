package local.accountvault.android.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import local.accountvault.android.AndroidThemePolicy as Theme

/** Native screen-like detail/editor. Back, IME, focus and accessibility remain Android-owned. */
class ScreenDialog(private val activity: Activity, private val title: String) : Dialog(activity, local.accountvault.android.R.style.ExplosionScreen) {
    val body = MobileUi.stack(activity, 20)
    private val footer = MobileUi.stack(activity, 16)
    var onBack: (() -> Unit)? = null
    override fun dismiss() { super.dismiss(); body.removeAllViews(); footer.removeAllViews() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val root = MobileUi.stack(activity).apply { setBackgroundColor(Color.parseColor(Theme.background)) }
        val toolbar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(MobileUi.dp(this, 12), MobileUi.dp(this, 8), MobileUi.dp(this, 20), MobileUi.dp(this, 8))
            setBackgroundColor(Color.parseColor(Theme.panel))
        }
        toolbar.addView(MobileUi.action(activity, I18n.t("Назад")) { requestBack() }, LinearLayout.LayoutParams(-2, -2))
        toolbar.addView(MobileUi.text(activity, title, 19f, true).apply { setPadding(MobileUi.dp(this, 12), 0, 0, 0); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(toolbar)
        root.addView(ScrollView(activity).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        if (footer.childCount > 0) root.addView(footer)
        setContentView(root)
        MobileUi.keyboardInsets(root)
        window?.setLayout(-1, -1)
    }
    fun action(text: String, primary: Boolean = false, onClick: () -> Unit) {
        footer.addView(MobileUi.action(activity, text, primary, onClick), MobileUi.gapParams(footer, 4))
    }
    fun requestBack() { onBack?.invoke() ?: dismiss() }
    @Deprecated("Native back callback") override fun onBackPressed() { requestBack() }
}
