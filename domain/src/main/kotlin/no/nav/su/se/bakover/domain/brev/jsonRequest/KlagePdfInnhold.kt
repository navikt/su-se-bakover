package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.Dokumenttype
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
    dødsbo = dødsbo,
)

fun KlageDokumentCommand.Avvist.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
) = KlagePdfInnhold.Avvist(
    sakstype = sakstype,
    personalia = personalia,
    saksbehandlerNavn = saksbehandlerNavn,
    attestantNavn = attestantNavn,
    fritekst = fritekst,
    saksnummer = saksnummer.nummer,
    dødsbo = dødsbo,
)

val klageOversendtBrev = Brevmal<KlageDokumentCommand.OpprettholdEllerDelvisOmgjøring>(
    dokumenttype = Dokumenttype.INFORMASJON_ANNET,
    brevtype = Brevtype.OVERSENDELSE_KA,
) { felles, kommando ->
    kommando.fromBrevCommand(
        personalia = felles.personalia,
        saksbehandlerNavn = felles.saksbehandlerNavn,
        attestantNavn = felles.attestantNavn ?: "-",
    )
}

val klageAvvistBrev = Brevmal<KlageDokumentCommand.Avvist>(
    dokumenttype = Dokumenttype.VEDTAK,
    brevtype = Brevtype.VEDTAK,
) { felles, kommando ->
    kommando.fromBrevCommand(
        personalia = felles.personalia,
        saksbehandlerNavn = felles.saksbehandlerNavn,
        attestantNavn = felles.attestantNavn ?: "-",
    )
}
