package no.nav.su.se.bakover.domain.regulering

import beregning.domain.Beregning
import økonomi.domain.simulering.Simulering

data class IverksattRegulering(
    /**
     * Denne er gjort public pga å gjøre den testbar fra databasen siden vi må kunne gjøre den persistert
     */
    val opprettetRegulering: OpprettetRegulering,
    override val beregning: Beregning,
    override val simulering: Simulering,
) : Regulering, Reguleringsfelter by opprettetRegulering {
    override fun erÅpen(): Boolean = false

    override val erFerdigstilt = true

    /**
     * Skal ikke sende brev ved regulering.
     */
    override fun skalSendeVedtaksbrev(): Boolean {
        return false
    }
}
