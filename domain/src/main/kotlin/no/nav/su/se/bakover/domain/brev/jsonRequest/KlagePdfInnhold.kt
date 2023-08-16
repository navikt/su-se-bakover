package no.nav.su.se.bakover.domain.brev.jsonRequest

import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand

sealed class KlagePdfInnhold : PdfInnhold() {
    data class Oppretthold(
        val personalia: PersonaliaPdfInnhold,
        val saksbehandlerNavn: String,
        val attestantNavn: String?,
        val fritekst: String,
        val klageDato: String,
        val vedtakDato: String,
        val saksnummer: Long,
    ) : KlagePdfInnhold() {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.Klage.Oppretthold

        companion object {
            fun fromBrevCommand(
                command: KlageDokumentCommand.Oppretthold,
                personalia: PersonaliaPdfInnhold,
                saksbehandlerNavn: String,
                attestantNavn: String,
            ): KlagePdfInnhold {
                return Oppretthold(
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
        val personalia: PersonaliaPdfInnhold,
        val saksbehandlerNavn: String,
        val attestantNavn: String?,
        val fritekst: String,
        val saksnummer: Long,
    ) : KlagePdfInnhold() {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.Klage.Avvist

        companion object {
            fun fromBrevCommand(
                command: KlageDokumentCommand.Avvist,
                personalia: PersonaliaPdfInnhold,
                saksbehandlerNavn: String,
                attestantNavn: String,
            ): KlagePdfInnhold {
                return Avvist(
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
