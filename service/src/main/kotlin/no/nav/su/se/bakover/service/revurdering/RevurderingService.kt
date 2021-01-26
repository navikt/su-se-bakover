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
        periode: Periode,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<RevurderingFeilet, Revurdering>

    fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        periode: Periode,
        fradrag: List<Fradrag>
    ): Either<RevurderingFeilet, RevurdertBeregning>

    fun sendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<RevurderingFeilet, Revurdering>

    fun lagBrevutkast(revurderingId: UUID, fritekst: String?): Either<RevurderingFeilet, ByteArray>
}

sealed class RevurderingFeilet {
    object GeneriskFeil : RevurderingFeilet()
    object FantIkkeSak : RevurderingFeilet()
    object FantIkkeRevurdering : RevurderingFeilet()
    object MicrosoftApiGraphFeil : RevurderingFeilet()
    object FantIngentingSomKanRevurderes : RevurderingFeilet()
    object FantIkkePerson : RevurderingFeilet()
    object KunneIkkeFinneAktørId : RevurderingFeilet()
    object KunneIkkeOppretteOppgave : RevurderingFeilet()
    object KunneIkkeLageBrevutkast : RevurderingFeilet()
}
