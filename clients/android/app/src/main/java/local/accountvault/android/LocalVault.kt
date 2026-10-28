package local.accountvault.android

import java.util.Base64
import java.util.UUID
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import local.accountvault.android.features.notes.VaultNote
import local.accountvault.android.features.notes.NoteCodec

data class VaultField(val label: String, val value: String)

data class VaultAccount(
    val id: String,
    val category: String,
    val name: String,
    val fields: List<VaultField>,
    val favorite: Boolean = false
)

class LocalVault(
    val accounts: MutableList<VaultAccount> = mutableListOf(),
    val notes: MutableList<VaultNote> = mutableListOf()
) {
    fun addAccount(category: String, name: String, fields: List<VaultField>): VaultAccount {
        val account = VaultAccount(UUID.randomUUID().toString(), category, name, fields.toList())
        accounts.add(account)
        return account
    }

    fun removeAccount(id: String): Boolean = accounts.removeIf { it.id == id }

    fun updateAccount(id: String, category: String, name: String, fields: List<VaultField>): Boolean {
        val index = accounts.indexOfFirst { it.id == id }
        if (index < 0) return false
        accounts[index] = VaultAccount(id, category, name, fields.toList(), accounts[index].favorite)
        return true
    }

    fun search(query: String): List<VaultAccount> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return accounts.toList()
        return accounts.filter { account ->
            sequenceOf(account.category, account.name)
                .plus(account.fields.flatMap { listOf(it.label, it.value) })
                .any { it.lowercase().contains(needle) }
        }
    }

    fun toPortableJson(): String {
        val categories = JsonArray()
        accounts.groupBy { it.category }.forEach { (category, categoryAccounts) ->
            val categoryJson = JsonObject().apply {
                addProperty("id", "category-${encode(category)}")
                addProperty("name", category)
                addProperty("icon", "▤")
                add("accounts", JsonArray().apply {
                    categoryAccounts.forEach { account ->
                        add(JsonObject().apply {
                            addProperty("id", account.id)
                            addProperty("name", account.name)
                            addProperty("favorite", account.favorite)
                            add("fields", JsonArray().apply {
                                account.fields.forEach { field -> add(JsonObject().apply {
                                    addProperty("id", "field-${UUID.randomUUID()}")
                                    addProperty("label", field.label)
                                    addProperty("value", field.value)
                                }) }
                            })
                        })
                    }
                })
            }
            categories.add(categoryJson)
        }
        return JsonObject().apply { add("categories", categories) }.toString()
    }

    fun searchNotes(query:String):List<VaultNote> = notes.filter{it.title.contains(query.trim(),true)||it.content.contains(query.trim(),true)||it.links.any{link->link.name.contains(query.trim(),true)||link.url.contains(query.trim(),true)}}.sortedWith(compareByDescending<VaultNote>{it.pinned}.thenByDescending{java.time.Instant.parse(it.updatedAt)})
    fun toBackupJson(includeNotes:Boolean=true):String {
        val root=JsonParser.parseString(toPortableJson()).asJsonObject
        if(includeNotes){NoteCodec.validate(notes);root.add("notes",NoteCodec.encode(notes))}
        return root.toString()
    }
    fun snapshot():String {
        val legacy=accountSnapshot()
        if(notes.isEmpty())return legacy
        NoteCodec.validate(notes)
        return JsonObject().apply{addProperty("format",2);addProperty("accountsSnapshot",legacy);add("notes",NoteCodec.encode(notes))}.toString()
    }
    private fun accountSnapshot(): String = accounts.joinToString("\n") { account ->
        listOf(
            encode(account.id),
            encode(account.category),
            encode(account.name),
            account.fields.joinToString(",") { field -> "${encode(field.label)}:${encode(field.value)}" },
            if(account.favorite)"1" else "0"
        ).joinToString("|")
    }

    companion object {
        fun fromPortableJson(json: String): LocalVault {
            val root = JsonParser.parseString(json)
            val categories = if(root.isJsonArray)root.asJsonArray else root.asJsonObject.getAsJsonArray("categories")
            require(categories!=null) { "Missing categories" }
            val vault = LocalVault()
            categories.forEach { categoryElement ->
                val category = categoryElement.asJsonObject
                val categoryName = category.get("name").asString
                val remoteAccounts = category.getAsJsonArray("accounts")
                remoteAccounts.forEach { accountElement ->
                    val remoteAccount = accountElement.asJsonObject
                    val remoteFields = remoteAccount.getAsJsonArray("fields") ?: JsonArray()
                    val fields = remoteFields.map { fieldElement ->
                        val field = fieldElement.asJsonObject
                        val label=field.get("label").asString
                        require(label.isNotBlank()) { "Missing field label" }
                        VaultField(label, field.get("value")?.asString ?: "")
                    }.toMutableList()
                    if(!remoteAccount.has("fields")){
                        for((key,label) in listOf("login" to "Почта","password" to "Пароль","note" to "Заметка")){
                            val value=remoteAccount.get(key)?.takeUnless{it.isJsonNull}?.asString
                            if(!value.isNullOrEmpty())fields.add(VaultField(label,value))
                        }
                    }
                    val account = VaultAccount(remoteAccount.get("id").asString, categoryName, remoteAccount.get("name").asString, fields,remoteAccount.get("favorite")?.asBoolean?:false)
                    require(account.id.isNotBlank() && account.name.isNotBlank() && categoryName.isNotBlank())
                    require(vault.accounts.none {it.id==account.id}) { "Duplicate account id" }
                    vault.accounts.add(account)
                }
            }
            if(root.isJsonObject&&root.asJsonObject.has("notes"))vault.notes.addAll(NoteCodec.decode(root.asJsonObject.getAsJsonArray("notes")))
            return vault
        }

        fun fromSnapshot(snapshot: String): LocalVault {
            if(snapshot.trimStart().startsWith("{")){
                val root=JsonParser.parseString(snapshot).asJsonObject
                require(root.get("format").asInt==2)
                return fromSnapshot(root.get("accountsSnapshot").asString).apply{notes.addAll(NoteCodec.decode(root.getAsJsonArray("notes")))}
            }
            val vault = LocalVault()
            snapshot.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split('|')
                require(parts.size in 4..5) { "Invalid vault account record" }
                val fields = if (parts[3].isBlank()) emptyList() else parts[3].split(',').map { item ->
                    val field = item.split(':', limit = 2)
                    require(field.size == 2) { "Invalid vault field" }
                    VaultField(decode(field[0]), decode(field[1]))
                }
                val id = decode(parts[0])
                val category = decode(parts[1])
                val name = decode(parts[2])
                require(id.isNotBlank() && category.isNotBlank() && name.isNotBlank()) { "Invalid vault account values" }
                if(parts.size==5)require(parts[4]=="0"||parts[4]=="1") { "Invalid favorite flag" }
                vault.accounts.add(VaultAccount(id, category, name, fields, parts.getOrNull(4)=="1"))
            }
            return vault
        }

        private fun encode(value: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

        private fun decode(value: String): String =
            try { String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8) }
            catch (_: IllegalArgumentException) { throw IllegalArgumentException("Invalid vault encoding") }
    }
}
