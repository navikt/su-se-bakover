package no.nav.su.se.bakover.domain.sak.fnr

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import java.util.UUID

data class OppdaterFødselsnummerPåSakCommand(
    val sakId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)
