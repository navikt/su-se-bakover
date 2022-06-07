package no.nav.su.se.bakover.client

import org.json.JSONObject
import java.time.LocalDateTime

open class ExpiringTokenResponse(
    private val json: JSONObject
) {
    val accessToken: AccessToken = AccessToken(json.getString("access_token"))
    private val expiresIn: Int = json.getInt("expires_in")
    private val expirationTime = LocalDateTime.now().plusSeconds(expiresIn - 20L)

    companion object {
        fun isValid(token: ExpiringTokenResponse?): Boolean {
            return when (token) {
                null -> false
                else -> !token.isExpired()
            }
        }
    }

    private fun isExpired() = expirationTime.isBefore(LocalDateTime.now())
}

fun ExpiringTokenResponse?.isValid(): Boolean {
    return this != null && ExpiringTokenResponse.isValid(this)
}
data class AccessToken(val token: String)
