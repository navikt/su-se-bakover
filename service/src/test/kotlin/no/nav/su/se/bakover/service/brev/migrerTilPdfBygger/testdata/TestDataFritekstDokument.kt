package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.FritekstPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype

val fritekstDokumentPdf = FritekstPdfInnhold(
    tittel = "Jeg er fritekst-dokumentets tittel",
    sakstype = Sakstype.UFØRE,
    saksbehandlerNavn = "Malenia, blade of Miquella",
    fritekst = "Jeg er fritekst-dokumentets fritekst. Jeg er en fri tekst. Jeg føler meg fri, og har alltid vært fri. Det gir ikke mening at en fri tekst ikke er fri. Frihet er stolthet.",
    personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021L,
    ),
)
