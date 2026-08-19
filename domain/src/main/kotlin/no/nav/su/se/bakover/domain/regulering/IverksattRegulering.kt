package no.nav.su.se.bakover.domain.regulering

import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import økonomi.domain.simulering.Simulering

data class IverksattRegulering(
    /**
     * Denne er gjort public pga å gjøre den testbar fra databasen siden vi må kunne gjøre den persistert
     */
    val opprettetRegulering: ReguleringUnderBehandling,
    override val beregning: Beregning,
    override val simulering: Simulering,
    val erSendtTilOppdrag: Boolean = true,
) : Regulering by opprettetRegulering {
    override fun erÅpen(): Boolean = false

    override val erFerdigstilt = true

    val attestering: Attestering
        get() = opprettetRegulering.attesteringer.hentSisteAttestering()

    override fun skalSendeVedtaksbrev() = false
}
