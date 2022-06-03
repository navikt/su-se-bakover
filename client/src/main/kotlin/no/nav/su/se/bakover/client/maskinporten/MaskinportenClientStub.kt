package no.nav.su.se.bakover.client.maskinporten

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.ExpiringTokenResponse

class MaskinportenClientStub : MaskinportenClient {
    override fun hentNyToken(): Either<KunneIkkeHenteToken, ExpiringTokenResponse> {
        return KunneIkkeHenteToken.Nettverksfeil(NotImplementedError("Skal ikke kalles på")).left()
    }
}
