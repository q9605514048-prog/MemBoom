package local.accountvault.android.features.security

import java.security.SecureRandom
import java.util.Collections

object PasswordGenerator {
    private val random = SecureRandom()
    private val groups = listOf("ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnopqrstuvwxyz", "23456789", "!@#$%^&*()-_=+")

    fun generate(length: Int = 20): String {
        require(length in 4..128) { "Password length must be between 4 and 128" }
        val characters = groups.map { it[random.nextInt(it.length)] }.toMutableList()
        val alphabet = groups.joinToString("")
        repeat(length - groups.size) { characters.add(alphabet[random.nextInt(alphabet.length)]) }
        Collections.shuffle(characters, random)
        return characters.joinToString("")
    }
}
