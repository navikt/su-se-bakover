package no.nav.su.se.bakover.client.person

import arrow.core.Either
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person

interface PersonOppslag {
    fun person(fnr: Fnr): Either<PdlFeil, Person>
    fun aktørId(fnr: Fnr): Either<PdlFeil, AktørId>
}
