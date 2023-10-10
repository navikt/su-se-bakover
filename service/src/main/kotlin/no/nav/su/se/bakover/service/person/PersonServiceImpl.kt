package no.nav.su.se.bakover.service.person

import arrow.core.Either
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import person.domain.PersonOppslag
import person.domain.PersonService

class PersonServiceImpl(
    private val personOppslag: PersonOppslag,
) : PersonService {
    override fun hentPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
        return personOppslag.person(fnr)
    }

    override fun hentPersonMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
        return personOppslag.personMedSystembruker(fnr)
    }

    override fun hentAktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
        return personOppslag.aktørId(fnr)
    }

    override fun hentAktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
        return personOppslag.aktørIdMedSystembruker(fnr)
    }

    override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> {
        return personOppslag.sjekkTilgangTilPerson(fnr)
    }
}
