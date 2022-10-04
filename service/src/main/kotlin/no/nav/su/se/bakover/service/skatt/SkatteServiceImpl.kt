package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import no.nav.su.se.bakover.client.ExpiringTokenResponse
import no.nav.su.se.bakover.client.isValid
import no.nav.su.se.bakover.client.maskinporten.KunneIkkeHenteToken
import no.nav.su.se.bakover.client.maskinporten.MaskinportenClient
import no.nav.su.se.bakover.client.skatteetaten.Skatteoppslag
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.Skattegrunnlag

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val maskinportenClient: MaskinportenClient,
) : SkatteService {

    private var maskinportenToken: ExpiringTokenResponse? = null

    @Synchronized
    private fun hentMaskinportenToken(): Either<KunneIkkeHenteToken, ExpiringTokenResponse> {
        if (maskinportenToken.isValid()) {
            return maskinportenToken!!.right()
        }
        return maskinportenClient.hentNyttToken()
            .tap { maskinportenToken = it }
    }

    override fun hentSamletSkattegrunnlag(fnr: Fnr): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag> {
        return hentMaskinportenToken()
            .mapLeft { KunneIkkeHenteSkattemelding.KunneIkkeHenteAccessToken(it) }
            .flatMap { tokenResponse ->
                skatteClient.hentSamletSkattegrunnlag(tokenResponse.accessToken, fnr)
                    .mapLeft { feil -> KunneIkkeHenteSkattemelding.KallFeilet(feil) }
                    .map { hentInntektOgFradrag(it) }
            }
    }

    private fun hentInntektOgFradrag(skattegrunnlag: Skattegrunnlag): Skattegrunnlag =
        skattegrunnlag.copy(
            grunnlag = skattegrunnlag.grunnlag.filter {
                it.kategori.contains(Skattegrunnlag.Kategori.INNTEKT) ||
                    it.kategori.contains(Skattegrunnlag.Kategori.FORMUE)
            },
        )
}
