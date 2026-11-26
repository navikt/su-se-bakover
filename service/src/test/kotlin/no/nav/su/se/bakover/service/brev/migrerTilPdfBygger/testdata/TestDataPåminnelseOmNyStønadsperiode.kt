package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.PåminnelseNyStønadsperiodePdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype

val påminnelseOmNyStønadsperiodePdf = PåminnelseNyStønadsperiodePdfInnhold(
    sakstype = Sakstype.UFØRE,
    personalia = PersonaliaPdfInnhold(
        dato = "01.01.2021",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 1234,
    ),
    utløpsdato = "31.03.2022",
    uføreSomFyller67 = true,
)
