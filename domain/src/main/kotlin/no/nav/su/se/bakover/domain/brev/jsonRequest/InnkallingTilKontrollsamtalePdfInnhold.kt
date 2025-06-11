package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class InnkallingTilKontrollsamtalePdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
) : PdfInnhold {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.InnkallingTilKontrollsamtale

    companion object {
        fun fromBrevCommand(
            personalia: PersonaliaPdfInnhold,
            sakstype: Sakstype,
        ): InnkallingTilKontrollsamtalePdfInnhold {
            return InnkallingTilKontrollsamtalePdfInnhold(
                sakstype = sakstype,
                personalia = personalia,
            )
        }
    }
}
