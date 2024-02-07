package no.nav.su.se.bakover.test.auth

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.auth.KunneIkkeHenteSamlToken
import no.nav.su.se.bakover.common.domain.auth.SamlToken
import no.nav.su.se.bakover.common.domain.auth.SamlTokenProvider
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Gyldig en time frem i tid.
 * @param clock Overstyr denne for å tvinge utgått-tidspunkt.
 */
class FakeSamlTokenProvider(
    private val clock: Clock = fixedClock,
    private val token: String = "fake-saml-token",
) : SamlTokenProvider {
    override fun samlToken(): Either<KunneIkkeHenteSamlToken, SamlToken> {
        return SamlToken(
            token = token,
            expirationTime = Tidspunkt.now(clock).plus(1, ChronoUnit.HOURS),
        ).right()
    }
}
