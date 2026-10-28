package local.accountvault.android.features.editor

import local.accountvault.android.LocalVault
import local.accountvault.android.VaultAccount
import local.accountvault.android.VaultField

class AccountEntryService(private val vault: LocalVault, private val persist: () -> Unit) {
    fun add(category: String, name: String, fields: List<VaultField>): VaultAccount {
        require(category.isNotBlank()) { "Укажите категорию" }
        require(name.isNotBlank()) { "Укажите название аккаунта" }
        require(fields.all{it.label.isNotBlank()}) { "Укажите названия полей" }
        val account = vault.addAccount(category.trim(), name.trim(), fields)
        try { persist() } catch (error: Exception) {
            vault.removeAccount(account.id)
            throw error
        }
        return account
    }
    fun update(id:String,category:String,name:String,fields:List<VaultField>):Boolean {
        require(category.isNotBlank()&&name.isNotBlank()&&fields.all{it.label.isNotBlank()})
        return change {vault.updateAccount(id,category.trim(),name.trim(),fields)}
    }
    fun remove(id:String):Boolean=change{vault.removeAccount(id)}
    private fun change(action:()->Boolean):Boolean {
        val before=vault.accounts.toList()
        if(!action())return false
        try{persist()}catch(error:Exception){vault.accounts.clear();vault.accounts.addAll(before);throw error}
        return true
    }
}
