package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID

data class NyBeregningForSøknadsbehandling(
    val behandlingId: UUID,
    val saksbehandler: Saksbehandler,
    val fradrag: List<Fradrag>,
)
