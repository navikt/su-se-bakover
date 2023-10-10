package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.PdfTemplateMedDokumentNavn

data class InnkallingTilKontrollsamtalePdfInnhold(
    val personalia: PersonaliaPdfInnhold,
) : PdfInnhold() {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.InnkallingTilKontrollsamtale

    companion object {
        fun fromBrevCommand(
            personalia: PersonaliaPdfInnhold,
        ): InnkallingTilKontrollsamtalePdfInnhold {
            return InnkallingTilKontrollsamtalePdfInnhold(
                personalia = personalia,
            )
        }
    }
}
