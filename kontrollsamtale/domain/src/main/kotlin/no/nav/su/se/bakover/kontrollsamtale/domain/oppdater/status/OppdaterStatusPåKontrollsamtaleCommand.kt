package no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import java.util.UUID

/**
 * @param nyStatus Merk at vi har begrensninger på hvilke status vi kan endre til.
 */
data class OppdaterStatusPåKontrollsamtaleCommand(
    val sakId: UUID,
    val kontrollsamtaleId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val nyStatus: OppdaterStatusTil,
) {
    /**
     * Støtter ikke å endre til annullert, da det er et eget endepunkt for det.
     * Støtter ikke planlagt innkalling, da vi ikke kan gå tilbake til denne tilstanden.
     * Støtter ikke innkalt, da vi knytter en utgående brev (dokumentId) til denne tilstanden. Dette sendes av SU-app.
     */
    sealed interface OppdaterStatusTil {
        data object IkkeMøttInnenFrist : OppdaterStatusTil
        data class Gjennomført(val journalpostId: JournalpostId) : OppdaterStatusTil
    }
}
