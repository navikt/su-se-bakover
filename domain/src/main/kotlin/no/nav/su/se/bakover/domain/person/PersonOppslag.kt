package no.nav.su.se.bakover.domain.person

import arrow.core.Either

interface PersonOppslag {
    fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person>
    fun personMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person>
    fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId>
    fun aktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId>
    fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit>
}

sealed class KunneIkkeHentePerson {
    object FantIkkePerson : KunneIkkeHentePerson()
    object IkkeTilgangTilPerson : KunneIkkeHentePerson()
    object Ukjent : KunneIkkeHentePerson()
}
