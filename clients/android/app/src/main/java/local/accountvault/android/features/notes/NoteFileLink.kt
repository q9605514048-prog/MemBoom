package local.accountvault.android.features.notes

import local.accountvault.android.ui.I18n

import java.net.URI
import java.util.Locale

data class NoteFileLink(val name:String,val url:String) {
    private fun uri():URI=try{URI(url.trim())}catch(_:Exception){throw IllegalArgumentException(I18n.t("Проверьте ссылку на файл"))}
    val provider:String get()=when(uri().host?.lowercase(Locale.ROOT)) {
        "drive.google.com","docs.google.com"->I18n.t("Google Диск")
        "disk.yandex.ru","disk.yandex.com","yadi.sk"->I18n.t("Яндекс Диск")
        else->throw IllegalArgumentException(I18n.t("Нужна ссылка на Google Диск или Яндекс Диск"))
    }
    fun validate(){
        require(name.isNotBlank()&&name.length<=160){I18n.t("Укажите название файла (до 160 символов)")}
        require(url.length<=4096){I18n.t("Ссылка на файл слишком длинная")}
        val parsed=uri()
        require(parsed.scheme.equals("https",true)&&parsed.rawUserInfo==null&&parsed.port in listOf(-1,443)){I18n.t("Нужна HTTPS-ссылка на Google Диск или Яндекс Диск")}
        provider
    }
    fun normalized()=copy(name=name.trim(),url=url.trim()).also{it.validate()}
    companion object {
        const val MAX_LINKS=20
        fun validateList(links:List<NoteFileLink>){
            require(links.size<=MAX_LINKS){I18n.t("Можно добавить до 20 ссылок на файлы")}
            links.forEach{it.validate()}
            require(links.map{it.url.trim()}.distinct().size==links.size){I18n.t("Эта ссылка на файл уже добавлена")}
        }
    }
}
