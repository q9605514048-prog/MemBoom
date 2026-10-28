package local.accountvault.android.features

import local.accountvault.android.MasterPasswordSession
import local.accountvault.android.features.security.PasswordGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PasswordGeneratorTest {
    @Test fun defaultPasswordContainsTwentyAllowedCharactersAndAllCharacterGroups() {
        val password = PasswordGenerator.generate()
        assertEquals(20, password.length)
        assertTrue(MasterPasswordSession.isValid(password.toCharArray()))
        assertTrue(password.any { it in 'a'..'z' })
        assertTrue(password.any { it in 'A'..'Z' })
        assertTrue(password.any { it in '0'..'9' })
        assertTrue(password.any { !it.isLetterOrDigit() })
    }

    @Test fun shortestPasswordStillContainsEveryCharacterGroup() {
        val password = PasswordGenerator.generate(4)
        assertEquals(4, password.length)
        assertTrue(password.any { it in 'a'..'z' })
        assertTrue(password.any { it in 'A'..'Z' })
        assertTrue(password.any { it in '0'..'9' })
        assertTrue(password.any { !it.isLetterOrDigit() })
    }

    @Test fun invalidLengthsAreRejected() {
        assertFailsWith<IllegalArgumentException> { PasswordGenerator.generate(3) }
        assertFailsWith<IllegalArgumentException> { PasswordGenerator.generate(129) }
    }
}
