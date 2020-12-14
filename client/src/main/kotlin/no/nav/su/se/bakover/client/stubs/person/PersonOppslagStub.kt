package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Telefonnummer
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag

object PersonOppslagStub :
    PersonOppslag {
    override fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person> =
        if (fnr.toString() == Config.fnrForPersonMedSkjerming) {
            Either.left(KunneIkkeHentePerson.IkkeTilgangTilPerson)
        } else {
            Person(
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
                        adresseformat = "Vegadresse"
                    )
                ),
                statsborgerskap = "NOR",
                kjønn = "MANN",
                adressebeskyttelse = null,
                skjermet = false,
                kontaktinfo = Person.Kontaktinfo(
                    epostadresse = "mail@epost.com",
                    mobiltelefonnummer = "90909090",
                    reservert = false,
                    kanVarsles = true,
                    språk = "nb"
                ),
                vergemål = null,
                fullmakt = null
            ).right()
        }

    override fun aktørId(fnr: Fnr) = AktørId("2437280977705").right()
}
