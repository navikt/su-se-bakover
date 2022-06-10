package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.client.maskinporten.MaskinportenClient
import no.nav.su.se.bakover.client.skatteetaten.Skatteoppslag
import no.nav.su.se.bakover.client.skatteetaten.SkatteoppslagFeil
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.SamletSkattegrunnlag

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val maskinportenClient: MaskinportenClient,
) : SkatteService {

    override fun hentSamletSkattegrunnlag(fnr: Fnr): Either<KunneIkkeHenteSkattemelding, SamletSkattegrunnlag> {
        return maskinportenClient.hentNyttToken()
            .mapLeft { KunneIkkeHenteSkattemelding.KunneIkkeHenteAccessToken(it) }
            .flatMap { tokenResponse ->
                skatteClient.hentSamletSkattegrunnlag(tokenResponse.accessToken, fnr)
                    .mapLeft { feil ->
                        KunneIkkeHenteSkattemelding.KallFeilet(feil).also { wrappedFeil ->
                            val detaljOmFeil = when (val originalFeil = wrappedFeil.feil) {
                                is SkatteoppslagFeil.KunneIkkeHenteSkattedata -> originalFeil.feilmelding
                                is SkatteoppslagFeil.Nettverksfeil -> "Fikk ikke koblet opp mot skatt på grunn av nettverk"
                            }
                            log.warn(
                                "Fikk ikke hentet skattemelding på grunn av feil i oppslaget: $detaljOmFeil. " +
                                    "Dette betyr at saksbehandler ikke får opp informasjon om inntekt og formue " +
                                    "fra skattemeldingen i saksbehandlingen."
                            )
                        }
                    }
            }
    }
}
