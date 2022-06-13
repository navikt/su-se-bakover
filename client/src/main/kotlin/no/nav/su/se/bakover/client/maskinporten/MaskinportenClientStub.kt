package no.nav.su.se.bakover.client.maskinporten

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.ExpiringTokenResponse
import org.json.JSONObject
import java.time.Clock

class MaskinportenClientStub(private val clock: Clock) : MaskinportenClient {
    override fun hentNyttToken(): Either<KunneIkkeHenteToken, ExpiringTokenResponse> {
        val stubData = """{"access_token": "access_token", "expires_in": "120"}"""
        return ExpiringTokenResponse(json = JSONObject(stubData), clock = clock).right()
    }
}
