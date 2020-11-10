package no.nav.su.se.bakover.client.dkif

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.Fnr

interface Dkif {
    fun hentKontaktinformasjon(fnr: Fnr): Either<ClientError, Kontaktinformasjon>
}
