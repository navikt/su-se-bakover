package no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.TrukketSøknadPdfInnhold
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand

fun TrukketSøknadDokumentCommand.toPdfInnhold(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
) = TrukketSøknadPdfInnhold(
    personalia = personalia,
    datoSøknadOpprettet = søknadOpprettet.toLocalDate(zoneIdOslo).ddMMyyyy(),
    trukketDato = trukketDato.ddMMyyyy(),
    saksbehandlerNavn = saksbehandlerNavn,
    sakstype = sakstype,
)
