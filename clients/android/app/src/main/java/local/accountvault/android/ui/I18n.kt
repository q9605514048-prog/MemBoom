package local.accountvault.android.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.widget.Button
import org.json.JSONObject
import java.util.Locale

object I18n {
    private var language="ru"
    private var translations:Map<String,String> = emptyMap()
    val localeTag get()=language
    fun wrap(base:Context):Context {
        val saved=base.getSharedPreferences("appearance",Context.MODE_PRIVATE).getString("language",null)
        language=if(saved in listOf("ru","en"))saved!! else if(base.resources.configuration.locales[0].language=="en")"en" else "ru"
        if(translations.isEmpty()){
            val json=JSONObject(base.assets.open("en.json").bufferedReader().use{it.readText()})
            translations=json.keys().asSequence().associateWith{json.getString(it)}
        }
        val locale=Locale.forLanguageTag(language)
        val config=Configuration(base.resources.configuration);config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
    fun t(key:String,vararg args:Any?):String {
        val value=if(language=="en")translations[key]?:key else key
        return Regex("\\{(\\d+)\\}").replace(value){args.getOrNull(it.groupValues[1].toInt())?.toString()?:""}
    }
    fun languageButton(activity:Activity)=Button(activity).apply{
        text="Language / Язык: "+if(language=="en")"English" else "Русский";MobileUi.quiet(this)
        setOnClickListener{
            AlertDialog.Builder(activity).setTitle("Language / Язык").setSingleChoiceItems(arrayOf("Русский","English"),if(language=="en")1 else 0){dialog,index->
                val selected=if(index==1)"en" else "ru"
                if(activity.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putString("language",selected).commit()){
                    dialog.dismiss();if(selected!=language)activity.recreate()
                }
            }.setNegativeButton(if(language=="en")"Cancel" else "Отмена",null).show()
        }
    }
    fun termsButton(activity:Activity)=Button(activity).apply{
        text=t("Пользовательское соглашение");MobileUi.quiet(this)
        setOnClickListener{
            val text=activity.assets.open("terms-$language.txt").bufferedReader().use{it.readText()}
            val body=android.widget.TextView(activity).apply{this.text=text;textSize=14f;setTextColor(android.graphics.Color.parseColor("#F9EEFA"));val pad=MobileUi.dp(this,20);setPadding(pad,pad,pad,pad);setLineSpacing(MobileUi.dp(this,4).toFloat(),1f)}
            AlertDialog.Builder(activity).setTitle(t("Пользовательское соглашение")).setView(android.widget.ScrollView(activity).apply{addView(body)}).setPositiveButton(t("Закрыть"),null).show()
        }
    }
}

open class LanguageActivity:Activity(){
    override fun attachBaseContext(newBase:Context){super.attachBaseContext(I18n.wrap(newBase))}
}
