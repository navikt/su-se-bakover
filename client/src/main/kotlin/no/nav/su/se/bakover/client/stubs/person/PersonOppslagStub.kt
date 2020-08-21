package no.nav.su.se.bakover.client.stubs.person

import arrow.core.right
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Telefonnummer

object PersonOppslagStub :
    PersonOppslag {
    override fun person(fnr: Fnr) = Person(
        ident = Ident(fnr, AktørId("aktørid")),
        navn = Person.Navn(
            fornavn = "Tore",
            mellomnavn = "Johnas",
            etternavn = "Strømøy"
        ),
        telefonnummer = Telefonnummer(landskode = "47", nummer = "12345678"),
        adresse = Person.Adresse(
            adressenavn = "Oslogata",
            husnummer = "12",
            husbokstav = null,
            postnummer = "0050",
            bruksenhet = "U1H20",
            kommunenummer = "0301"
        ),
        statsborgerskap = "NOR",
        kjønn = "MANN"
    ).right()

    override fun aktørId(fnr: Fnr) = AktørId("aktørid").right()
}
