package local.accountvault.android

object MasterPasswordSession {
    fun isValid(password: CharArray): Boolean =
        password.size >= 4 && password.all { it.code in 0x21..0x7E }

    fun requireValid(password: String): CharArray {
        require(isValid(password.toCharArray())) { "Master password must contain at least 4 ASCII letters, digits, or symbols" }
        return password.toCharArray()
    }

    fun clearAfterUse(@Suppress("UNUSED_PARAMETER") password: String): String = ""
}
