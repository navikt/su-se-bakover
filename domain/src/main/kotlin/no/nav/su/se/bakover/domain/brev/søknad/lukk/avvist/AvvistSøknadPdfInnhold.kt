package no.nav.su.se.bakover.domain.brev.søknad.lukk.avvist

import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.AvvistSøknadVedtakPdfInnhold
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand

fun AvvistSøknadDokumentCommand.tilAvvistSøknadPdfInnhold(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
): PdfInnhold {
    return AvvistSøknadVedtakPdfInnhold(
        sakstype = sakstype,
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        fritekst = fritekst,
    )
}
