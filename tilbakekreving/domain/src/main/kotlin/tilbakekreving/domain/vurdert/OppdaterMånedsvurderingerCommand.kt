package tilbakekreving.domain.vurdert

import arrow.core.Nel
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

data class OppdaterMånedsvurderingerCommand(
    val vurderinger: List<Månedsvurdering>,
    val sakId: UUID,
    val behandlingsId: UUID,
    val utførtAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
    val brukerroller: Nel<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
)
