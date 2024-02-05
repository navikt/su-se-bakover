package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.YearRange

data class SøknadsbehandlingSkattCommand(
    val behandlingId: SøknadsbehandlingId,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val yearRange: YearRange,
)
