package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.Dokumenttype
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.FritekstPdfInnhold
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand

fun FritekstDokumentCommand.toPdfInnhold(personalia: PersonaliaPdfInnhold, saksbehandlerNavn: String) =
    FritekstPdfInnhold(
        sakstype = sakstype,
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        tittel = brevTittel,
        fritekst = fritekst,
    )

/** Fritekstbrevet finnes i tre varianter, én per distribusjonstype. Innholdet er det samme. */
val fritekstVedtakBrev = fritekstBrev(Dokumenttype.VEDTAK, Brevtype.FRITEKST_VEDTAK)
val fritekstViktigBrev = fritekstBrev(Dokumenttype.INFORMASJON_VIKTIG, Brevtype.FRITEKST_VIKTIG)
val fritekstAnnetBrev = fritekstBrev(Dokumenttype.INFORMASJON_ANNET, Brevtype.FRITEKST_ANNET)

private fun fritekstBrev(dokumenttype: Dokumenttype, brevtype: Brevtype) =
    Brevmal<FritekstDokumentCommand>(dokumenttype, brevtype) { felles, kommando ->
        kommando.toPdfInnhold(personalia = felles.personalia, saksbehandlerNavn = felles.saksbehandlerNavn)
    }
