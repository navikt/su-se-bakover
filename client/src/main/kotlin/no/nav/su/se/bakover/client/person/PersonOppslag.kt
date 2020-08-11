package no.nav.su.se.bakover.client.person

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr

interface PersonOppslag {
    fun person(fnr: Fnr): Either<ClientError, PdlData>
    fun aktørId(fnr: Fnr): Either<ClientError, AktørId>
}
