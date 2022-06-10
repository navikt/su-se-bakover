package no.nav.su.se.bakover.client

import org.json.JSONObject
import java.time.LocalDateTime

open class ExpiringTokenResponse(
    json: JSONObject
) {
    val accessToken: AccessToken = AccessToken(json.getString("access_token"))
    private val expiresIn: Long = json.getLong("expires_in")
    private val expirationTime = LocalDateTime.now().plusSeconds(expiresIn - 20)

    companion object {
        fun isValid(token: ExpiringTokenResponse?): Boolean = token?.isExpired() == false
    }

    private fun isExpired() = expirationTime.isBefore(LocalDateTime.now())
}

fun ExpiringTokenResponse?.isValid(): Boolean {
    return this != null && ExpiringTokenResponse.isValid(this)
}
data class AccessToken(val value: String)
