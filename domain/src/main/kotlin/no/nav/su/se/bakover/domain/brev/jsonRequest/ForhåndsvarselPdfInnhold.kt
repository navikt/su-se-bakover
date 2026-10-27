package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.pdf.PersonaliaPdfInnhold
import dokument.domain.pdf.bygger.maler.ForhåndsvarselPdfInnhold
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand

fun ForhåndsvarselDokumentCommand.toPdfInnhold(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
): ForhåndsvarselPdfInnhold {
    return ForhåndsvarselPdfInnhold(
        sakstype = sakstype,
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        fritekst = fritekst,
    )
}
