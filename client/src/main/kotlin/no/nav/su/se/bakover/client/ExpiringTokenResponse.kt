package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.common.domain.auth.AccessToken
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDateTime

open class ExpiringTokenResponse(
    json: JSONObject,
    private val clock: Clock,
) {
    val accessToken: AccessToken = AccessToken(json.getString("access_token"))
    private val expiresIn: Long = json.getLong("expires_in")
    private val expirationTime = LocalDateTime.now(clock).plusSeconds(expiresIn - 20)

    companion object {
        fun isValid(token: ExpiringTokenResponse?): Boolean = token?.isExpired() == false
    }

    private fun isExpired() = expirationTime.isBefore(LocalDateTime.now(clock))
}

fun ExpiringTokenResponse?.isValid(): Boolean {
    return this != null && ExpiringTokenResponse.isValid(this)
}
