package no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket

import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.Dokumenttype
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.TrukketSøknadPdfInnhold
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand

fun TrukketSøknadDokumentCommand.toPdfInnhold(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
) = TrukketSøknadPdfInnhold(
    personalia = personalia,
    // TODO PdfBygger ryddejobb: Gi datoSøknadOpprettet nytt navn, datoSøknadMottatt, når PDFGen er faset ut (feltet er kontrakt mot søknadTrukket.hbs)
    datoSøknadOpprettet = søknadMottatt.ddMMyyyy(),
    trukketDato = trukketDato.ddMMyyyy(),
    saksbehandlerNavn = saksbehandlerNavn,
    sakstype = sakstype,
)

val trukketSøknadBrev = Brevmal<TrukketSøknadDokumentCommand>(
    dokumenttype = Dokumenttype.INFORMASJON_ANNET,
    brevtype = Brevtype.SØKNAD_TRUKKET,
) { felles, kommando ->
    kommando.toPdfInnhold(personalia = felles.personalia, saksbehandlerNavn = felles.saksbehandlerNavn)
}
