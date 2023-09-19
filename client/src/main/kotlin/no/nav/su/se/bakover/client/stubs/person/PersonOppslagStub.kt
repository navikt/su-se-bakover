package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.person.Telefonnummer
import person.domain.KunneIkkeHentePerson

data object PersonOppslagStub :
    PersonOppslag {

    fun nyTestPerson(fnr: Fnr) = Person(
        ident = Ident(fnr, AktørId("2437280977705")),
        navn = Person.Navn(
            fornavn = "Tore",
            mellomnavn = "Johnas",
            etternavn = "Strømøy",
        ),
        telefonnummer = Telefonnummer(landskode = "+47", nummer = "12345678"),
        adresse = listOf(
            Person.Adresse(
                adresselinje = "Oslogata 12",
                bruksenhet = "U1H20",
                poststed = Person.Poststed(postnummer = "0050", poststed = "OSLO"),
                kommune = Person.Kommune(kommunenummer = "0301", kommunenavn = "OSLO"),
                adressetype = "Bostedsadresse",
                adresseformat = "Vegadresse",
            ),
        ),
        statsborgerskap = "NOR",
        sivilstand = null,
        fødsel = Person.Fødsel.MedFødselsdato(
            dato = 1.januar(1990),
        ),
        adressebeskyttelse = if (fnr.toString() == ApplicationConfig.fnrKode6()) "STRENGT_FORTROLIG_ADRESSE" else null,
        skjermet = false,
        kontaktinfo = Person.Kontaktinfo(
            epostadresse = "mail@epost.com",
            mobiltelefonnummer = "90909090",
            språk = "nb",
            kanKontaktesDigitalt = true,
        ),
        vergemål = null,
        fullmakt = null,
        dødsdato = 21.januar(2021),
    )

    override fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person> =
        if (fnr.toString() == ApplicationConfig.fnrKode6()) {
            KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
        } else {
            nyTestPerson(fnr).right()
        }

    override fun personMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> = nyTestPerson(fnr).right()
    override fun aktørId(fnr: Fnr) = AktørId("2437280977705").right()
    override fun aktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> =
        AktørId("2437280977705").right()

    override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> =
        if (fnr.toString() == ApplicationConfig.fnrKode6()) {
            KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
        } else {
            Unit.right()
        }
}
