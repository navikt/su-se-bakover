package no.nav.su.se.bakover.common.domain.auth

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock
import java.time.Duration

private val EXPIRATION_MARGIN = Duration.ofSeconds(10)

class SamlToken(
    val token: String,
    private val expirationTime: Tidspunkt,
) {
    fun isExpired(clock: Clock) = expirationTime <= Tidspunkt.now(clock).plus(EXPIRATION_MARGIN)

    override fun toString(): String = token
}
