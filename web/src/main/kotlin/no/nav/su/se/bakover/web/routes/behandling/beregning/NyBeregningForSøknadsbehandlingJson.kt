package no.nav.su.se.bakover.web.routes.behandling.beregning

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.NyBeregningForSøknadsbehandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.beregning.FradragJson.Companion.toFradrag
import java.util.UUID

internal data class NyBeregningForSøknadsbehandlingJson(
    val fradrag: List<FradragJson>,
) {
    fun toDomain(behandlingId: UUID, saksbehandler: Saksbehandler): Either<Resultat, NyBeregningForSøknadsbehandling> {
        val fradrag = fradrag.toFradrag().getOrHandle {
            return it.left()
        }
        return NyBeregningForSøknadsbehandling(
            behandlingId = behandlingId,
            saksbehandler = saksbehandler,
            fradrag = fradrag,
        ).right()
    }
}
