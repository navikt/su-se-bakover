package no.nav.su.se.bakover.client.stubs.dkif

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dkif.Dkif
import no.nav.su.se.bakover.client.dkif.Kontaktinformasjon
import no.nav.su.se.bakover.domain.Fnr

object DkifClientStub : Dkif {
    override fun hentKontaktinformasjon(fnr: Fnr): Either<ClientError, Kontaktinformasjon> {
        return Kontaktinformasjon(
            epostadresse = "mail@epost.com",
            mobiltelefonnummer = "90909090",
            reservert = false,
            kanVarsles = true,
            språk = "nb"
        ).right()
    }
}
