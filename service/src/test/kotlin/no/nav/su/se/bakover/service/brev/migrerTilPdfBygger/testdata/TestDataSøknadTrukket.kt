package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.TrukketSøknadPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype

val søknadTrukketPdf = TrukketSøknadPdfInnhold(
    sakstype = Sakstype.UFØRE,
    saksbehandlerNavn = "",
    personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021L,
    ),
    datoSøknadOpprettet = "01.08.2020",
    trukketDato = "03.08.2020",
)
