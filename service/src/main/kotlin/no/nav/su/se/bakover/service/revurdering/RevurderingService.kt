package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID

interface RevurderingService {

    fun opprettRevurdering(
        sakId: UUID,
        periode: Periode
    ): Either<RevurderingFeilet, Revurdering>

    fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        periode: Periode,
        fradrag: List<Fradrag>
    ): Either<RevurderingFeilet, RevurdertBeregning>
}

sealed class RevurderingFeilet {
    object GeneriskFeil : RevurderingFeilet()
}

