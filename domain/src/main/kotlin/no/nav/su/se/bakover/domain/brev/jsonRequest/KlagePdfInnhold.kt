package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand

sealed interface KlagePdfInnhold : PdfInnhold {
    data class Oppretthold(
        override val sakstype: Sakstype,
        val personalia: PersonaliaPdfInnhold,
        val saksbehandlerNavn: String,
        val attestantNavn: String?,
        val fritekst: String,
        val klageDato: String,
        val vedtakDato: String,
        val saksnummer: Long,
    ) : KlagePdfInnhold {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.Klage.Oppretthold

        companion object {
            fun fromBrevCommand(
                command: KlageDokumentCommand.OpprettholdEllerDelvisOmgjøring,
                personalia: PersonaliaPdfInnhold,
                saksbehandlerNavn: String,
                attestantNavn: String,
            ): KlagePdfInnhold {
                return Oppretthold(
                    sakstype = command.sakstype,
                    personalia = personalia,
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn,
                    fritekst = command.fritekst,
                    klageDato = command.klageDato.ddMMyyyy(),
                    vedtakDato = command.vedtaksbrevDato.ddMMyyyy(),
                    saksnummer = command.saksnummer.nummer,
                )
            }
        }
    }

    data class Avvist(
        override val sakstype: Sakstype,
        val personalia: PersonaliaPdfInnhold,
        val saksbehandlerNavn: String,
        val attestantNavn: String?,
        val fritekst: String,
        val saksnummer: Long,
    ) : KlagePdfInnhold {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.Klage.Avvist

        companion object {
            fun fromBrevCommand(
                command: KlageDokumentCommand.Avvist,
                personalia: PersonaliaPdfInnhold,
                saksbehandlerNavn: String,
                attestantNavn: String,
            ): KlagePdfInnhold {
                return Avvist(
                    sakstype = command.sakstype,
                    personalia = personalia,
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn,
                    fritekst = command.fritekst,
                    saksnummer = command.saksnummer.nummer,
                )
            }
        }
    }
}
