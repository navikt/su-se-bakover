package no.nav.su.se.bakover.domain.brev.jsonRequest

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand

@JsonPropertyOrder(
    "personalia",
    "saksbehandlerNavn",
    "tittel",
    "fritekst",
    "sakstype",
)
data class FritekstPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val tittel: String,
    val fritekst: String,
) : PdfInnhold {
    override val dokumentMal: DokumentMal = DokumentMal.FritekstDokument(tittel)

    companion object {
        fun fromBrevCommand(
            command: FritekstDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
        ): FritekstPdfInnhold {
            return FritekstPdfInnhold(
                sakstype = command.sakstype,
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                tittel = command.brevTittel,
                fritekst = command.fritekst,
            )
        }
    }
}
