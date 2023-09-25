package tilbakekreving.domain.opprett

import arrow.core.Nel
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import java.util.UUID

data class OpprettTilbakekrevingsbehandlingCommand(
    val sakId: UUID,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
    val brukerroller: Nel<Brukerrolle>,
)
