package no.nav.su.se.bakover.domain.behandlinger.stopp

import java.util.UUID

interface StoppbehandlingRepo {
    fun opprettStoppbehandling(nyBehandling: Stoppbehandling.Simulert): Stoppbehandling.Simulert

    /**
     * Verifiserer samtidig at vi kun har 0 eller 1 pågående stoppbehandling.
     * Foreløpig har vi bare en ferdig status: Stoppbehandling.Iverksatt
     */
    fun hentPågåendeStoppbehandling(sakId: UUID): Stoppbehandling?

    object KunneIkkeOppretteStoppbehandling
}
