package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.InnkallingTilKontrollsamtalePdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype

val innkallingKontrollsamtalePdf = InnkallingTilKontrollsamtalePdfInnhold(
    sakstype = Sakstype.UFØRE,
    personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021L,
    ),
)
