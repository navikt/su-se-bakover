package tilbakekreving.domain.iverksettelse

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse

/**
 * @param correlationId En request-chain i nav har ofte en correlation id. Denne kan komme fra frontend, et eksternt system eller være generert i presentasjonslaget.
 * @param ident Brukeren som utførte handlingen og rollen vi tilegnet den.
 * @param brukerroller Rollene brukeren har som har tilknytning til dette systemet.
 */
data class IverksattHendelseMetadata(
    override val correlationId: CorrelationId,
    override val ident: NavIdentBruker,
    override val brukerroller: List<Brukerrolle>,
    val tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse,
) : HendelseMetadata
