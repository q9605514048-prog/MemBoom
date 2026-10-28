package local.accountvault.android.features.notes

import java.net.URI
import java.util.Locale

data class NoteFileLink(val name:String,val url:String) {
    private fun uri():URI=try{URI(url.trim())}catch(_:Exception){throw IllegalArgumentException("Проверьте ссылку на файл")}
    val provider:String get()=when(uri().host?.lowercase(Locale.ROOT)) {
        "drive.google.com","docs.google.com"->"Google Диск"
        "disk.yandex.ru","disk.yandex.com","yadi.sk"->"Яндекс Диск"
        else->throw IllegalArgumentException("Нужна ссылка на Google Диск или Яндекс Диск")
    }
    fun validate(){
        require(name.isNotBlank()&&name.length<=160){"Укажите название файла (до 160 символов)"}
        require(url.length<=4096){"Ссылка на файл слишком длинная"}
        val parsed=uri()
        require(parsed.scheme.equals("https",true)&&parsed.rawUserInfo==null&&parsed.port in listOf(-1,443)){"Нужна HTTPS-ссылка на Google Диск или Яндекс Диск"}
        provider
    }
    fun normalized()=copy(name=name.trim(),url=url.trim()).also{it.validate()}
    companion object {
        const val MAX_LINKS=20
        fun validateList(links:List<NoteFileLink>){
            require(links.size<=MAX_LINKS){"Можно добавить до 20 ссылок на файлы"}
            links.forEach{it.validate()}
            require(links.map{it.url.trim()}.distinct().size==links.size){"Эта ссылка на файл уже добавлена"}
        }
    }
}
