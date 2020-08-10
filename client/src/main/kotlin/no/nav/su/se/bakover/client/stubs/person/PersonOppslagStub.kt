package no.nav.su.se.bakover.client.stubs.person

import arrow.core.right
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Adresse
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Telefonnummer

object PersonOppslagStub :
    PersonOppslag {
    override fun person(fnr: Fnr) = Person(
        fnr = fnr,
        aktørId = AktørId("aktørid"),
        navn = Navn(
            fornavn = "Tore",
            mellomnavn = "Johnas",
            etternavn = "Strømøy"
        ),
        telefonnummer = Telefonnummer(landskode = "47", nummer = "12345678"),
        adresse = Adresse(
            adressenavn = "Oslogata",
            husnummer = "12",
            husbokstav = null,
            postnummer = "0050",
            poststed = "Oslo",
            bruksenhet = "U1H20",
            kommunenavn = "Oslo",
            kommunenummer = "0301"
        ),
        statsborgerskap = "NOR",
        kjønn = "MANN"

    ).right()

    override fun aktørId(fnr: Fnr) = AktørId("aktørid").right()
}
