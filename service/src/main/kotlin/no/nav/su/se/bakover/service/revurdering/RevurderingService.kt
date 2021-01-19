package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.time.LocalDate
import java.util.UUID

interface RevurderingService {
    fun beregnOgSimuler(
        sakId: UUID,
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeBeregneEllerSimulere, Beregning>
}

object SimulertOK
object KunneIkkeBeregneEllerSimulere

