package no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand

data class TrukketSøknadPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val datoSøknadOpprettet: String,
    val trukketDato: String,
    val saksbehandlerNavn: String,
    override val sakstype: Sakstype,
) : PdfInnhold {
    override val dokumentMal: DokumentMal = DokumentMal.TrukketSøknad

    companion object {
        fun fromBrevCommand(
            command: TrukketSøknadDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
        ): TrukketSøknadPdfInnhold {
            return TrukketSøknadPdfInnhold(
                personalia = personalia,
                datoSøknadOpprettet = command.søknadOpprettet.toLocalDate(zoneIdOslo).ddMMyyyy(),
                trukketDato = command.trukketDato.ddMMyyyy(),
                saksbehandlerNavn = saksbehandlerNavn,
                sakstype = command.sakstype,
            )
        }
    }
}
