package no.nav.su.se.bakover.domain.brev.søknad.lukk.avvist

import dokument.domain.brev.Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst
import dokument.domain.brev.Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.AvvistSøknadFritekstPdfInnhold
import dokument.domain.generer.bygger.maler.AvvistSøknadVedtakPdfInnhold
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand

fun AvvistSøknadDokumentCommand.tilAvvistSøknadPdfInnhold(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
): PdfInnhold {
    return when (brevvalg) {
        is InformasjonsbrevMedFritekst -> AvvistSøknadFritekstPdfInnhold(
            sakstype = sakstype,
            personalia = personalia,
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = this.brevvalg.fritekst,
        )

        is Vedtaksbrev -> AvvistSøknadVedtakPdfInnhold(
            sakstype = sakstype,
            personalia = personalia,
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = this.brevvalg.fritekst,
        )
    }
}
