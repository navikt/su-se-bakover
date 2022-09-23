package no.nav.su.se.bakover.domain.person

import arrow.core.Either
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person

interface PersonService {
    fun hentPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Person>
    fun hentPersonMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person>
    fun hentAktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId>
    fun hentAktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId>
    fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit>
}
