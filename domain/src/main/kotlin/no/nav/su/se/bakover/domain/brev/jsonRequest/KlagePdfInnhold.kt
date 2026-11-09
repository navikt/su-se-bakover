package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.KlagePdfInnhold
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand

fun KlageDokumentCommand.OpprettholdEllerDelvisOmgjøring.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
) = KlagePdfInnhold.Oppretthold(
    sakstype = sakstype,
    personalia = personalia,
    saksbehandlerNavn = saksbehandlerNavn,
    attestantNavn = attestantNavn,
    fritekst = fritekst,
    klageDato = klageDato.ddMMyyyy(),
    vedtakDato = vedtaksbrevDato?.ddMMyyyy(),
    saksnummer = saksnummer.nummer,
)

fun KlageDokumentCommand.Avvist.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
): KlagePdfInnhold {
    return KlagePdfInnhold.Avvist(
        sakstype = sakstype,
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = attestantNavn,
        fritekst = fritekst,
        saksnummer = saksnummer.nummer,
    )
}
