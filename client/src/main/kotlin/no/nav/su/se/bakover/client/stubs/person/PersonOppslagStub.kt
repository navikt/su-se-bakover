package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.person.PdlFeil
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Telefonnummer

object PersonOppslagStub :
    PersonOppslag {
    override fun person(fnr: Fnr): Either<PdlFeil, Person> = Person(
        ident = Ident(fnr, AktørId("2437280977705")),
        navn = Person.Navn(
            fornavn = "Tore",
            mellomnavn = "Johnas",
            etternavn = "Strømøy"
        ),
        telefonnummer = Telefonnummer(landskode = "+47", nummer = "12345678"),
        adresse = Person.Adresse(
            adressenavn = "Oslogata",
            husnummer = "12",
            husbokstav = null,
            bruksenhet = "U1H20",
            poststed = Person.Poststed(postnummer = "0050", poststed = "OSLO"),
            kommune = Person.Kommune(kommunenummer = "0301", kommunenavn = "OSLO")
        ),
        statsborgerskap = "NOR",
        kjønn = "MANN",
        adressebeskyttelse = null,
        skjermet = false
    ).right()

    override fun aktørId(fnr: Fnr) = AktørId("2437280977705").right()
}
