package no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId

data class SøknadsbehandlingSkattCommand(
    val behandlingId: SøknadsbehandlingId,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val yearRange: YearRange,
)
