package no.nav.su.se.bakover.service.person

import arrow.core.Either
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.person.PersonService

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
