package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.AvsluttRevurderingPdfInnhold
import no.nav.su.se.bakover.domain.brev.command.AvsluttRevurderingDokumentCommand

fun AvsluttRevurderingDokumentCommand.toPdfInnhold(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
) = AvsluttRevurderingPdfInnhold(
    sakstype = sakstype,
    personalia = personalia,
    saksbehandlerNavn = saksbehandlerNavn,
    fritekst = fritekst,
)
