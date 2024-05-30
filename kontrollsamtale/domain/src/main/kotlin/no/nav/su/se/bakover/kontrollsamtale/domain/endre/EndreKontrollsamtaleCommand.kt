package no.nav.su.se.bakover.kontrollsamtale.domain.endre

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.util.UUID

/**
 * @param nyInnkallingsmåned Dersom null, endrer vi ikke innkallingsmåned.
 * @param nyStatus Dersom null, endrer vi ikke status. Merk at vi har begrensninger på hvilke status vi kan endre til.
 */
data class EndreKontrollsamtaleCommand(
    val sakId: UUID,
    val kontrollsamtaleId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val nyInnkallingsmåned: Måned?,
    val nyStatus: EndreStatusTil?,
) {
    /**
     * Støtter ikke å endre til annullert, da det er et eget endepunkt for det.
     * Støtter ikke planlagt innkalling, da vi ikke kan gå tilbake til denne tilstanden.
     */
    sealed interface EndreStatusTil {
        data object Innkalt : EndreStatusTil
        data object IkkeMøttInnenFrist : EndreStatusTil
        data class Gjennomført(val journalpostId: JournalpostId) : EndreStatusTil
    }
}
