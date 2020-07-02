package no.nav.su.person.sts

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.person.sts.StsToken.Companion.isValid
import org.json.JSONObject
import java.time.LocalDateTime

interface TokenOppslag {
    fun token(): String
}

class StsClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) : TokenOppslag {
    private var stsToken: StsToken? = null

    override fun token(): String {
        if (!isValid(stsToken)) {
            val (_, _, result) = "$baseUrl/rest/v1/sts/token?grant_type=client_credentials&scope=openid".httpGet()
                .authentication().basic(username, password)
                .header("Accept", "application/json")
                .responseString()

            stsToken = result.fold(
                { StsToken(JSONObject(it)) },
                { throw RuntimeException("Error while getting token from STS, message:${it.message}, error:${String(it.errorData)}") }
            )
        }
        return stsToken?.accessToken!!
    }
}

data class StsToken(
    private val json: JSONObject
) {
    val accessToken: String = json.getString("access_token")
    private val expiresIn: Int = json.getInt("expires_in")
    private val expirationTime = LocalDateTime.now().plusSeconds(expiresIn - 20L)

    companion object {
        fun isValid(token: StsToken?): Boolean {
            return when (token) {
                null -> false
                else -> !isExpired(token)
            }
        }

        private fun isExpired(token: StsToken) = token.expirationTime.isBefore(LocalDateTime.now())
    }
}
