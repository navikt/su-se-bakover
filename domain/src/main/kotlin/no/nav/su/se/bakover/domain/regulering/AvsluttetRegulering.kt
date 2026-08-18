package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

data class AvsluttetRegulering(
    val opprettetRegulering: ReguleringUnderBehandling,
    override val avsluttetTidspunkt: Tidspunkt,
    override val avsluttetAv: NavIdentBruker?,
    val begrunnelse: String,
) : Regulering by opprettetRegulering,
    Avbrutt {
    override fun erÅpen() = false
    override fun erAvsluttet() = true
    override fun erAvbrutt() = true
    override val erFerdigstilt = true

    /**
     * Skal ikke sende brev ved regulering.
     */
    override fun skalSendeVedtaksbrev(): Boolean {
        return false
    }
}
