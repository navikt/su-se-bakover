package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.Dokumenttype
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

val forhåndsvarselBrev = Brevmal<ForhåndsvarselDokumentCommand>(
    dokumenttype = Dokumenttype.INFORMASJON_VIKTIG,
    brevtype = Brevtype.FORHANDSVARSEL,
) { felles, kommando ->
    kommando.toPdfInnhold(personalia = felles.personalia, saksbehandlerNavn = felles.saksbehandlerNavn)
}
