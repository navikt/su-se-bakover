package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.client.ExpiringTokenResponse
import no.nav.su.se.bakover.common.ApplicationConfig.ClientsConfig.SkatteetatenConfig
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Skattemelding
import java.net.http.HttpClient
import java.time.Duration

class SkatteClient(private val skatteetatenConfig: SkatteetatenConfig) : SkatteOppslag {
    private var token: ExpiringTokenResponse? = null

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun hentSkattemelding(accessToken: AccessToken, fnr: Fnr): Either<Feil, Skattemelding> {
        return Skattemelding(123).right()
    }
}
