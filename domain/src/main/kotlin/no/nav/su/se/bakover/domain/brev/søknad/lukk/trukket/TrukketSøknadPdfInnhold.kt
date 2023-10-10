package no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket

import dokument.domain.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.jsonRequest.PdfInnhold
import no.nav.su.se.bakover.domain.brev.jsonRequest.PersonaliaPdfInnhold

data class TrukketSøknadPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val datoSøknadOpprettet: String,
    val trukketDato: String,
    val saksbehandlerNavn: String,
) : PdfInnhold() {
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
