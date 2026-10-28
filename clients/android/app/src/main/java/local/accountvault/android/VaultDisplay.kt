package local.accountvault.android

object VaultDisplay {
    fun visibleValue(field: VaultField): String {
        val label = field.label.trim().lowercase()
        val secretLabels = listOf("парол", "password", "passcode", "token", "secret", "ключ", "key")
        return if (secretLabels.any(label::contains)) "••••••" else field.value
    }

    fun format(vault: LocalVault): String = vault.accounts.joinToString("\n\n") { account ->
        buildString {
            append(account.category).append(" / ").append(account.name)
            account.fields.forEach { field ->
                append("\n").append(field.label).append(": ").append(visibleValue(field))
            }
        }
    }
}
