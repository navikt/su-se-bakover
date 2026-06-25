package no.nav.su.se.bakover.service.person

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.BorPåAdresse
import person.domain.BorPåAdresseRequest
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import person.domain.PersonMedSkjermingOgKontaktinfo
import person.domain.PersonOppslag
import person.domain.PersonRepo
import person.domain.PersonService
import person.domain.PersonerOgSakstype
import java.util.UUID

class PersonServiceImpl(
    private val personOppslag: PersonOppslag,
    private val personRepo: PersonRepo,
) : PersonService {
    override fun hentPerson(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Person> {
        return personOppslag.person(fnr, sakstype)
    }

    override fun hentPersonMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Person> {
        return personOppslag.personMedSystembruker(fnr, sakstype)
    }

    override fun hentPersonMedSkjermingOgKontaktinfo(
        fnr: Fnr,
        sakstype: Sakstype,
    ): Either<KunneIkkeHentePerson, PersonMedSkjermingOgKontaktinfo> {
        return personOppslag.personMedSkjermingOgKontaktinfo(fnr, sakstype)
    }

    override fun hentAktørIdMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, AktørId> {
        return personOppslag.aktørIdMedSystembruker(fnr, sakstype)
    }

    override fun sjekkTilgangTilPerson(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Unit> {
        return personOppslag.sjekkTilgangTilPerson(fnr, sakstype)
    }

    override fun hentFnrForSak(sakId: UUID): PersonerOgSakstype {
        return personRepo.hentFnrOgSaktypeForSak(sakId)
    }

    override fun borPåAdresse(
        fnr: Fnr,
        sakstype: Sakstype,
    ): Either<KunneIkkeHentePerson, BorPåAdresse> {
        val person = hentPerson(fnr, sakstype).getOrElse {
            return Either.Left(it)
        }
        val adresse = person.adresse?.firstOrNull() ?: return Either.Left(KunneIkkeHentePerson.FantIkkePerson) // TODO
        val postnummer = adresse.poststed?.postnummer ?: return Either.Left(KunneIkkeHentePerson.FantIkkePerson) // TODO
        val adresselinjeSplit = adresse.adresselinje?.split(" ")
            ?: return Either.Left(KunneIkkeHentePerson.FantIkkePerson) // TODO

        val borPåAdresseRequest = BorPåAdresseRequest(
            adressenavn = adresselinjeSplit.dropLast(1).joinToString(" "),
            husnummer = adresselinjeSplit.last(),
            postnummer = postnummer,
        )
        return personOppslag.borPåAdresse(borPåAdresseRequest, sakstype)
    }
}
