package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

data class AvsluttetRegulering(
    val opprettetRegulering: OpprettetRegulering,
    override val avsluttetTidspunkt: Tidspunkt,
    override val avsluttetAv: NavIdentBruker?,
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
