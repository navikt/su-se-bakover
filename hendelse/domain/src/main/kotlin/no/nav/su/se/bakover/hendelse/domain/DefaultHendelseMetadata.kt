package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker

/**
 * @param correlationId En request-chain i nav har ofte en correlation id. Denne kan komme fra frontend, et eksternt system eller være generert i presentasjonslaget.
 * @param ident Brukeren som utførte handlingen og rollen vi tilegnet den.
 * @param brukerroller Rollene brukeren har som har tilknytning til dette systemet.
 */
data class DefaultHendelseMetadata(
    override val correlationId: CorrelationId?,
    override val ident: NavIdentBruker?,
    override val brukerroller: List<Brukerrolle>,
) : HendelseMetadata {
    companion object {
        fun tom() = DefaultHendelseMetadata(null, null, emptyList())
        fun fraCorrelationId(correlationId: CorrelationId) = DefaultHendelseMetadata(
            correlationId = correlationId,
            ident = null,
            brukerroller = emptyList(),
        )
    }
}
