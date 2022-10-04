package no.nav.su.se.bakover.client.krr

import arrow.core.Either
import no.nav.su.se.bakover.common.Fnr

interface KontaktOgReservasjonsregister {
    fun hentKontaktinformasjon(fnr: Fnr): Either<KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon>

    sealed interface KunneIkkeHenteKontaktinformasjon {
        object BrukerErIkkeRegistrert : KunneIkkeHenteKontaktinformasjon
        object FeilVedHenting : KunneIkkeHenteKontaktinformasjon
    }
}
