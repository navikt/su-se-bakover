package person.domain

import arrow.core.Either
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import java.util.UUID

interface PersonService {
    fun hentPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Person>
    fun hentPersonMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person>
    fun hentAktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId>
    fun hentAktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId>
    fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit>

    /** Henter fødselsnumrene knyttet til saken. Dette inkluderer alle registrerte EPS. */
    fun hentFnrForSak(sakId: UUID): List<Fnr>
}
