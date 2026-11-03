package local.accountvault.android.ui

import local.accountvault.android.LocalVault
import local.accountvault.android.VaultAccount
import local.accountvault.android.VaultField
import java.util.Locale

/** View state only: never changes the vault or its portable format. */
data class AccountFilter(val category: String? = null, val favoritesOnly: Boolean = false, val sort: AccountSort = AccountSort.NAME) {
    fun apply(vault: LocalVault, query: String): List<VaultAccount> {
        val matches = vault.search(query).filter { (category == null || it.category == category) && (!favoritesOnly || it.favorite) }
        val byName = compareBy<VaultAccount> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id }
        return when (sort) {
            AccountSort.NAME -> matches.sortedWith(byName)
            AccountSort.CATEGORY -> matches.sortedWith(compareBy<VaultAccount> { it.category.lowercase(Locale.ROOT) }.then(byName))
            AccountSort.FAVORITES -> matches.sortedWith(compareByDescending<VaultAccount> { it.favorite }.then(byName))
        }
    }
}

enum class AccountSort { NAME, CATEGORY, FAVORITES }

object FieldPresentation {
    fun isSecret(label: String):Boolean {
        val name=label.trim().lowercase(Locale.ROOT)
        val publicLabels=setOf("логин","почта","email","e-mail","username","user name","login","url","ссылка","сайт","website")
        return !name.split(Regex("\\s*[/|,]\\s*")).all { it in publicLabels }
    }
    fun loginPreview(account: VaultAccount): String? = account.fields.firstOrNull {
        !isSecret(it.label) && it.value.isNotBlank() && listOf("логин", "почт", "login", "mail", "user").any(it.label.lowercase(Locale.ROOT)::contains)
    }?.value
}

/** A new detail screen always starts masked; no secret participates in accessibility labels. */
class FieldDisclosure(private val field: VaultField) {
    var revealed = false
        private set
    val secret get() = FieldPresentation.isSecret(field.label)
    val displayValue get() = if (secret && !revealed && field.value.isNotEmpty()) "••••••••" else field.value
    fun toggle() { if (secret) revealed = !revealed }
    fun hide() { revealed = false }
}

/** Favorite is an existing model flag; this UI command preserves fields and rolls back failed writes. */
object AccountFavoriteAction {
    fun toggle(vault: LocalVault, id: String, persist: () -> Unit): Boolean {
        val index = vault.accounts.indexOfFirst { it.id == id }
        if (index < 0) return false
        val before = vault.accounts[index]
        vault.accounts[index] = before.copy(favorite = !before.favorite)
        try { persist() } catch (error: Exception) { vault.accounts[index] = before; throw error }
        return true
    }
}
