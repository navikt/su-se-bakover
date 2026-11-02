package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.ForhåndsvarselPdfInnhold
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
