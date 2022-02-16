package no.nav.su.se.bakover.client.dkif

import arrow.core.Either
import no.nav.su.se.bakover.domain.person.Fnr

interface DigitalKontaktinformasjon {
    fun hentKontaktinformasjon(fnr: Fnr): Either<KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon>

    object KunneIkkeHenteKontaktinformasjon
}
