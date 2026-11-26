package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.bygger.maler.KontrollnotatPdfInnholdNy
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import person.domain.Person

val kontrollnotatPdf = KontrollnotatPdfInnholdNy(
    sakstype = Sakstype.UFØRE,
    saksnummer = Saksnummer(2021),
    navn = Person.Navn(
        fornavn = "Leeroy",
        mellomnavn = "Johnas",
        etternavn = "Jenkins",
    ),
    dagensDatoOgTidspunkt = "01.01.2021 12:00",
    personligOppmøte = true,
    fullmaktOgLegeerklæring = false,
    originalPass = true,
    gyldigPass = false,
    harVærtUtenlands = true,
    utenlandsoppholdDatoer = listOf("01.01.2020 - 31.01.2020", "01.03.2020 - 05.03.2020"),
    harPlanerOmUtenlandsreise = true,
    planlagteUtenlandsreiseDatoer = listOf("01.07.2021 - 31.07.2021"),
    reiseDokumentasjon = true,
    økonomiskSituasjon = false,
    andreForhold = true,
    skatteOpplysninger = true,
    fritekst = "Bruker møtte presis.\nHadde med alle dokumenter.\n\nIngen merknader.",

)
