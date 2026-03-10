package person.domain

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr

interface PersonOppslag {
    fun person(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Person>
    fun personMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Person>
    fun bostedsadresseMedMetadataForSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AdresseopplysningerMedMetadata>
    fun personMedSkjermingOgKontaktinfo(
        fnr: Fnr,
        sakstype: Sakstype,
    ): Either<KunneIkkeHentePerson, PersonMedSkjermingOgKontaktinfo>
    fun aktørIdMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, AktørId>
    fun sjekkTilgangTilPerson(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Unit>
}
