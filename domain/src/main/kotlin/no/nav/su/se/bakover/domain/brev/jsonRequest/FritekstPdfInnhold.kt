package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.FritekstPdfInnhold
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand

fun FritekstDokumentCommand.toBrevCommand(personalia: PersonaliaPdfInnhold, saksbehandlerNavn: String) =
    FritekstPdfInnhold(
        sakstype = sakstype,
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        tittel = brevTittel,
        fritekst = fritekst,
    )
