package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.client.maskinporten.MaskinportenClient
import no.nav.su.se.bakover.client.skatteetaten.Skatteoppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.SamletSkattegrunnlag

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val maskinportenClient: MaskinportenClient,
) : SkatteService {
    override fun hentSamletSkattegrunnlag(fnr: Fnr): Either<KunneIkkeHenteSkattemelding, SamletSkattegrunnlag> {
        return maskinportenClient.hentNyToken()
            .mapLeft { KunneIkkeHenteSkattemelding.KunneIkkeHenteAccessToken(it) }
            .flatMap { tokenResponse ->
                skatteClient.hentSamletSkattegrunnlag(tokenResponse.accessToken, fnr)
                    .mapLeft { KunneIkkeHenteSkattemelding.SkatteErSlem }
            }
    }
}
