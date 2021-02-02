package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID

interface RevurderingService {

    fun opprettRevurdering(
        sakId: UUID,
        periode: Periode,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeRevurdere, Revurdering>

    fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeRevurdere, SimulertRevurdering>

    fun sendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeRevurdere, Revurdering>

    fun lagBrevutkast(revurderingId: UUID, fritekst: String?): Either<KunneIkkeRevurdere, ByteArray>
}

sealed class KunneIkkeRevurdere {
    object FantIkkeSak : KunneIkkeRevurdere()
    object FantIkkeRevurdering : KunneIkkeRevurdere()
    object MicrosoftApiGraphFeil : KunneIkkeRevurdere()
    object FantIngentingSomKanRevurderes : KunneIkkeRevurdere()
    object FantIkkePerson : KunneIkkeRevurdere()
    object KunneIkkeFinneAktørId : KunneIkkeRevurdere()
    object KunneIkkeOppretteOppgave : KunneIkkeRevurdere()
    object KunneIkkeLageBrevutkast : KunneIkkeRevurdere()
    object KanIkkeRevurdereInneværendeMånedEllerTidligere : KunneIkkeRevurdere()
    object KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder : KunneIkkeRevurdere()
    object SimuleringFeilet : KunneIkkeRevurdere()
}
