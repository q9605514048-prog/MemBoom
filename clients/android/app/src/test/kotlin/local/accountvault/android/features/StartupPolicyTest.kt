package local.accountvault.android.features

import kotlin.test.*
import local.accountvault.android.features.auth.*
import java.time.Instant

class StartupPolicyTest {
    private val now=Instant.parse("2026-10-27T00:00:00Z")
    private val session=AccountSession("http://10.0.2.2:8788","mbs_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG","2027-10-27T00:00:00Z","qa","qa_user")
    @Test fun freshOrLoggedOutInstallationRequiresAccountChoice(){assertFalse(StartupPolicy.mayResume(null,now))}
    @Test fun localModeOpensPasswordScreenWithoutRequiringServer(){
        assertFalse(StartupPolicy.requiresAccountChoice(null,true,now))
        assertTrue(StartupPolicy.requiresAccountChoice(null,false,now))
        assertFalse(StartupPolicy.requiresAccountChoice(session,false,now))
    }
    @Test fun rememberedSignInOpensWithoutAnotherPassword(){assertTrue(StartupPolicy.mayResume(session,now))}
    @Test fun expiredAndDamagedSignInsReturnToAccountChoice(){assertFalse(StartupPolicy.mayResume(session.copy(expiresAt="bad"),now));assertFalse(StartupPolicy.mayResume(session.copy(expiresAt="2025-01-01T00:00:00Z"),now))}
}
