package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Avbrutt

data class AvsluttetRegulering(
    val opprettetRegulering: OpprettetRegulering,
    override val avsluttetTidspunkt: Tidspunkt,
) : Regulering, Reguleringsfelter by opprettetRegulering, Avbrutt {
    override fun erÅpen(): Boolean = false
    override val erFerdigstilt = true

    /**
     * Skal ikke sende brev ved regulering.
     */
    override fun skalSendeVedtaksbrev(): Boolean {
        return false
    }
}
