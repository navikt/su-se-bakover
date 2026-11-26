package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.ForhåndsvarselPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype

val forhåndsvarselPdf = ForhåndsvarselPdfInnhold(
    sakstype = Sakstype.UFØRE,
    saksbehandlerNavn = "",
    personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021L,
    ),
    fritekst = "Her kommer det en lang fritekst om hvorfor vi vurderer saken din på nytt.",
)
