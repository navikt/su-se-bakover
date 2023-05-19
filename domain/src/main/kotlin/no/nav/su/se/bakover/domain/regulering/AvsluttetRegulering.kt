package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.tid.Tidspunkt

data class AvsluttetRegulering(
    val opprettetRegulering: OpprettetRegulering,
    val avsluttetTidspunkt: Tidspunkt,
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
