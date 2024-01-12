package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.YearRange
import java.util.UUID

data class SøknadsbehandlingSkattCommand(
    val behandlingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val yearRange: YearRange,
)
