package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Telefonnummer
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import java.time.LocalDate

object PersonOppslagStub :
    PersonOppslag {

    fun nyTestPerson(fnr: Fnr) = Person(
        ident = Ident(fnr, AktørId("2437280977705")),
        navn = Person.Navn(
            fornavn = "Tore",
            mellomnavn = "Johnas",
            etternavn = "Strømøy"
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
        kjønn = "MANN",
        fødselsdato = LocalDate.of(1990, 1, 1),
        sivilstand = Person.Sivilstand(
            type = SivilstandTyper.GIFT,
            relatertVedSivilstand = Fnr("15116414950"),
        ),
        adressebeskyttelse = null,
        skjermet = false,
        kontaktinfo = Person.Kontaktinfo(
            epostadresse = "mail@epost.com",
            mobiltelefonnummer = "90909090",
            reservert = false,
            kanVarsles = true,
            språk = "nb",
        ),
        vergemål = null,
        fullmakt = null,
    )

    override fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person> = nyTestPerson(fnr).right()
    override fun personMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> = nyTestPerson(fnr).right()
    override fun aktørId(fnr: Fnr) = AktørId("2437280977705").right()
    override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> = Unit.right()
}
