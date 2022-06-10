package no.nav.su.se.bakover.client.maskinporten

import arrow.core.Either
import no.nav.su.se.bakover.client.ExpiringTokenResponse

interface MaskinportenClient {
    fun hentNyttToken(): Either<KunneIkkeHenteToken, ExpiringTokenResponse>
}
