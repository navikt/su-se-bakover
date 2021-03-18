package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

interface RevurderingService {

    fun hentRevurdering(
        revurderingId: UUID,
    ): Either<FantIkkeRevurdering, Revurdering>

    fun opprettRevurdering(
        sakId: UUID,
        fraOgMed: LocalDate,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeOppretteRevurdering, Revurdering>

    fun oppdaterRevurderingsperiode(
        revurderingId: UUID,
        fraOgMed: LocalDate,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeOppdatereRevurderingsperiode, OpprettetRevurdering>

    fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, Revurdering>

    fun sendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering>

    fun lagBrevutkast(revurderingId: UUID, fritekst: String?): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray>
    fun iverksett(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering>

    fun hentRevurderingForUtbetaling(utbetalingId: UUID30): IverksattRevurdering?

    fun leggTilUføregrunnlag(revurderingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>): Either<KunneIkkeLeggeTilGrunnlag, LeggTilUføregrunnlagResponse>
}

object FantIkkeRevurdering

sealed class KunneIkkeOppretteRevurdering {
    object FantIkkeSak : KunneIkkeOppretteRevurdering()
    object FantIngentingSomKanRevurderes : KunneIkkeOppretteRevurdering()
    object FantIkkeAktørId : KunneIkkeOppretteRevurdering()
    object KunneIkkeOppretteOppgave : KunneIkkeOppretteRevurdering()
    object KanIkkeRevurdereInneværendeMånedEllerTidligere : KunneIkkeOppretteRevurdering()
    object KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder : KunneIkkeOppretteRevurdering()
    object KanIkkeRevurdereEnPeriodeMedEksisterendeRevurdering : KunneIkkeOppretteRevurdering()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeOppretteRevurdering()
}

sealed class KunneIkkeOppdatereRevurderingsperiode {
    object FantIkkeRevurdering : KunneIkkeOppdatereRevurderingsperiode()
    data class PeriodenMåVæreInnenforAlleredeValgtStønadsperiode(val periode: Periode) : KunneIkkeOppdatereRevurderingsperiode()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeOppdatereRevurderingsperiode()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) : KunneIkkeOppdatereRevurderingsperiode()
}

sealed class KunneIkkeBeregneOgSimulereRevurdering {
    object FantIkkeRevurdering : KunneIkkeBeregneOgSimulereRevurdering()
    object SimuleringFeilet : KunneIkkeBeregneOgSimulereRevurdering()
    object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneOgSimulereRevurdering()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeBeregneOgSimulereRevurdering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) : KunneIkkeBeregneOgSimulereRevurdering()
    object UfullstendigBehandlingsinformasjon : KunneIkkeBeregneOgSimulereRevurdering()
}

sealed class KunneIkkeSendeRevurderingTilAttestering {
    object FantIkkeRevurdering : KunneIkkeSendeRevurderingTilAttestering()
    object FantIkkeAktørId : KunneIkkeSendeRevurderingTilAttestering()
    object KunneIkkeOppretteOppgave : KunneIkkeSendeRevurderingTilAttestering()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeSendeRevurderingTilAttestering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) : KunneIkkeSendeRevurderingTilAttestering()
}

sealed class KunneIkkeIverksetteRevurdering {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteRevurdering()
    object KunneIkkeUtbetale : KunneIkkeIverksetteRevurdering()
    object KunneIkkeKontrollsimulere : KunneIkkeIverksetteRevurdering()
    object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : KunneIkkeIverksetteRevurdering()
    object KunneIkkeJournalføreBrev : KunneIkkeIverksetteRevurdering()
    object FantIkkeRevurdering : KunneIkkeIverksetteRevurdering()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>
    ) : KunneIkkeIverksetteRevurdering()
}

sealed class KunneIkkeLageBrevutkastForRevurdering {
    object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForRevurdering()
    object FantIkkePerson : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForRevurdering()
}

sealed class KunneIkkeLeggeTilGrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlag()
    object UgyldigStatus : KunneIkkeLeggeTilGrunnlag()
}

data class LeggTilUføregrunnlagResponse(
    val revurdering: Revurdering,
    val simulertEndringGrunnlag: GrunnlagService.SimulertEndringGrunnlag
)
