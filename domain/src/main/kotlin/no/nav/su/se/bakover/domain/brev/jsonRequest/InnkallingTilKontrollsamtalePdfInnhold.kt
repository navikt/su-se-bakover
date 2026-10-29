package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class InnkallingTilKontrollsamtalePdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.InnkallingTilKontrollsamtale

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
