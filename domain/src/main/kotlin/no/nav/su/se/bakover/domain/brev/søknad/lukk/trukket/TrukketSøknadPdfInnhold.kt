package no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand

data class TrukketSøknadPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val datoSøknadOpprettet: String,
    val trukketDato: String,
    val saksbehandlerNavn: String,
) : PdfInnhold {
    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.TrukketSøknad

    companion object {
        fun fromBrevCommand(
            command: TrukketSøknadDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
        ): TrukketSøknadPdfInnhold {
            return TrukketSøknadPdfInnhold(
                personalia = personalia,
                datoSøknadOpprettet = command.søknadOpprettet.toLocalDate(zoneIdOslo).ddMMyyyy(),
                trukketDato = command.trukketDato.ddMMyyyy(),
                saksbehandlerNavn = saksbehandlerNavn,
            )
        }
    }
}
