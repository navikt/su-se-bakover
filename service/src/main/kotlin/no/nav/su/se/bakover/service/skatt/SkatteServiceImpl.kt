package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.client.maskinporten.MaskinportenClient
import no.nav.su.se.bakover.client.skatteetaten.SkatteOppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Skattemelding

class SkatteServiceImpl(private val skatteClient: SkatteOppslag, private val maskinportenClient: MaskinportenClient) :
    SkatteService {
    override fun hentSkattemelding(fnr: Fnr): Either<KunneIkkeHenteSkattemelding, Skattemelding> {
        return maskinportenClient.hentNyToken()
            .mapLeft { KunneIkkeHenteSkattemelding.KunneIkkeHenteAccessToken(it) }
            .flatMap { tokenResponse ->
                skatteClient.hentSkattemelding(tokenResponse.accessToken, fnr)
                    .mapLeft { KunneIkkeHenteSkattemelding.SkatteErSlem }
            }
    }
}
