package no.nav.su.se.bakover.client.stubs.krr

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.krr.KontaktOgReservasjonsregister
import no.nav.su.se.bakover.client.krr.Kontaktinformasjon
import no.nav.su.se.bakover.common.person.Fnr

object KontaktOgReservasjonsregisterStub : KontaktOgReservasjonsregister {
    override fun hentKontaktinformasjon(fnr: Fnr): Either<KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon> {
        return Kontaktinformasjon(
            epostadresse = "mail@epost.com",
            mobiltelefonnummer = "90909090",
            reservert = false,
            kanVarsles = true,
            språk = "nb",
        ).right()
    }
}
