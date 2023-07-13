package no.nav.su.se.bakover.client.krr

import arrow.core.Either
import no.nav.su.se.bakover.common.person.Fnr

interface KontaktOgReservasjonsregister {
    fun hentKontaktinformasjon(fnr: Fnr): Either<KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon>

    sealed interface KunneIkkeHenteKontaktinformasjon {
        data object BrukerErIkkeRegistrert : KunneIkkeHenteKontaktinformasjon
        data object FeilVedHenting : KunneIkkeHenteKontaktinformasjon
    }
}
