package local.accountvault.android.features

import local.accountvault.android.features.auth.AccountAuthClient
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.*

class AccountAuthTest {
    private val session="""{"token":"mbs_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG","expiresAt":"2099-01-01T00:00:00Z","user":{"id":"qa-user","login":"qa_user"}}"""
    @Test fun supportsLoginAndPasswordWithoutRedirects(){
        val requests=mutableListOf<Fake>()
        val client=AccountAuthClient("http://10.0.2.2:8788"){url->Fake(url,200,session).also{requests.add(it)}}
        assertEquals("qa_user",client.login("qa_user","QaPass!123").login)
        assertEquals("/api/auth/login",requests.last().url.path)
        assertFalse(requests.last().instanceFollowRedirects)
        assertTrue(requests.first().bytes.toString("UTF-8").contains("qa_user"))
        assertTrue(requests.first().bytes.toString("UTF-8").contains("\"remember\":true"))
    }
    @Test fun registrationAndValidationErrorsAreExplicit(){
        val client=AccountAuthClient("https://example.test"){url->Fake(url,201,session)}
        assertEquals("qa_user",client.register("qa_user","QaPass!123").login)
        assertFails{AccountAuthClient("http://example.test")}
        assertFails{AccountAuthClient("https://user:pass@example.test")}
    }
    @Test fun homePcLoginWorksWithoutAllowingArbitraryHttp(){
        val client=AccountAuthClient("http://192.168.3.2:8788"){url->Fake(url,200,session)}
        assertEquals("http://192.168.3.2:8788",client.login("qa_user","QaPass!123").endpoint)
        assertFails{AccountAuthClient("http://192.168.3.2.evil.test:8788")}
        assertFails{AccountAuthClient("http://user:pass@192.168.3.2:8788")}
        assertFails{AccountAuthClient("http://192.168.3.20:8788")}
    }
    private class Fake(url:URL,private val status:Int,private val json:String):HttpURLConnection(url){
        val bytes=ByteArrayOutputStream()
        override fun connect(){};override fun disconnect(){};override fun usingProxy()=false
        override fun getResponseCode()=status
        override fun getOutputStream()=bytes
        override fun getInputStream()=ByteArrayInputStream(json.toByteArray())
        override fun getErrorStream()=ByteArrayInputStream(json.toByteArray())
    }
}
