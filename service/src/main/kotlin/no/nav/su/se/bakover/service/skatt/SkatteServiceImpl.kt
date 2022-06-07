package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import no.nav.su.se.bakover.client.ExpiringTokenResponse
import no.nav.su.se.bakover.client.isValid
import no.nav.su.se.bakover.client.maskinporten.KunneIkkeHenteToken
import no.nav.su.se.bakover.client.maskinporten.MaskinportenClient
import no.nav.su.se.bakover.client.skatteetaten.Skatteoppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.SamletSkattegrunnlag

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val maskinportenClient: MaskinportenClient,
) : SkatteService {

    private var skatteetatenToken: ExpiringTokenResponse? = null

    private fun medToken(): Either<KunneIkkeHenteToken, ExpiringTokenResponse> {
        if (skatteetatenToken.isValid()) {
            return skatteetatenToken!!.right()
        }
        return maskinportenClient.hentNyToken()
            .tap { token -> skatteetatenToken = token }
    }

    override fun hentSamletSkattegrunnlag(fnr: Fnr): Either<KunneIkkeHenteSkattemelding, SamletSkattegrunnlag> {
        return medToken()
            .mapLeft { KunneIkkeHenteSkattemelding.KunneIkkeHenteAccessToken(it) }
            .flatMap { tokenResponse ->
                skatteClient.hentSamletSkattegrunnlag(tokenResponse.accessToken, fnr)
                    .mapLeft { KunneIkkeHenteSkattemelding.SkatteErSlem }
            }
    }
}
