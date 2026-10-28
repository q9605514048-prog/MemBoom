package local.accountvault.android.features.auth

object StartupPolicy {
    fun requiresAccountChoice(session:AccountSession?,localMode:Boolean,now:java.time.Instant=java.time.Instant.now())=!mayResume(session,now)&&!localMode
    fun mayResume(session:AccountSession?,now:java.time.Instant=java.time.Instant.now()):Boolean = try {
        session!=null && session.login.isNotBlank() && session.token.startsWith("mbs_") && java.time.Instant.parse(session.expiresAt).isAfter(now)
    } catch(_:Exception){false}
}
