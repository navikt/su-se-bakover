package person.domain

import arrow.core.Either
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr

interface PersonOppslag {
    fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person>
    fun personMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person>
    fun aktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId>
    fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit>
}
