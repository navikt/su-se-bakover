package no.nav.su.se.bakover.client.stubs.person

import arrow.core.right
import no.nav.su.se.bakover.client.person.PdlData
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Telefonnummer

object PersonOppslagStub :
    PersonOppslag {
    override fun person(fnr: Fnr) = PdlData(
        ident = PdlData.Ident(fnr, AktørId("aktørid")),
        navn = PdlData.Navn(
            fornavn = "Tore",
            mellomnavn = "Johnas",
            etternavn = "Strømøy"
        ),
        telefonnummer = Telefonnummer(landskode = "47", nummer = "12345678"),
        adresse = PdlData.Adresse(
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
