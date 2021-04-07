package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import java.util.UUID
import kotlin.reflect.KClass

interface RevurderingService {

    fun hentRevurdering(
        revurderingId: UUID,
    ): Either<FantIkkeRevurdering, Revurdering>

    fun opprettRevurdering(
        opprettRevurderingRequest: OpprettRevurderingRequest,
    ): Either<KunneIkkeOppretteRevurdering, Revurdering>

    fun oppdaterRevurderingsperiode(
        oppdaterRevurderingRequest: OppdaterRevurderingRequest,
    ): Either<KunneIkkeOppdatereRevurderingsperiode, OpprettetRevurdering>

    fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradrag: List<Fradrag>,
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, Revurdering>

    fun sendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekstTilBrev: String,
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering>

    fun lagBrevutkast(revurderingId: UUID, fritekst: String): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray>
    fun hentBrevutkast(revurderingId: UUID): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray>
    fun iverksett(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering>

    fun underkjenn(
        revurderingId: UUID,
        attestering: Attestering.Underkjent,
    ): Either<KunneIkkeUnderkjenneRevurdering, UnderkjentRevurdering>

    fun leggTilUføregrunnlag(revurderingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>): Either<KunneIkkeLeggeTilGrunnlag, LeggTilUføregrunnlagResponse>
    fun hentUføregrunnlag(revurderingId: UUID): Either<KunneIkkeHenteGrunnlag, GrunnlagService.SimulertEndringGrunnlag>
}

object FantIkkeRevurdering

sealed class KunneIkkeOppretteRevurdering {
    object FantIkkeSak : KunneIkkeOppretteRevurdering()
    object FantIngentingSomKanRevurderes : KunneIkkeOppretteRevurdering()
    object FantIkkeAktørId : KunneIkkeOppretteRevurdering()
    object KunneIkkeOppretteOppgave : KunneIkkeOppretteRevurdering()
    object KanIkkeRevurdereInneværendeMånedEllerTidligere : KunneIkkeOppretteRevurdering()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeOppretteRevurdering()
    object UgyldigÅrsak : KunneIkkeOppretteRevurdering()
    object UgyldigBegrunnelse : KunneIkkeOppretteRevurdering()
}

sealed class KunneIkkeOppdatereRevurderingsperiode {
    object FantIkkeRevurdering : KunneIkkeOppdatereRevurderingsperiode()
    data class PeriodenMåVæreInnenforAlleredeValgtStønadsperiode(val periode: Periode) :
        KunneIkkeOppdatereRevurderingsperiode()

    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeOppdatereRevurderingsperiode()
    object UgyldigÅrsak : KunneIkkeOppdatereRevurderingsperiode()
    object UgyldigBegrunnelse : KunneIkkeOppdatereRevurderingsperiode()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeOppdatereRevurderingsperiode()
}

sealed class KunneIkkeBeregneOgSimulereRevurdering {
    object FantIkkeRevurdering : KunneIkkeBeregneOgSimulereRevurdering()
    object SimuleringFeilet : KunneIkkeBeregneOgSimulereRevurdering()
    object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneOgSimulereRevurdering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeBeregneOgSimulereRevurdering()

    object UfullstendigBehandlingsinformasjon : KunneIkkeBeregneOgSimulereRevurdering()
}

sealed class KunneIkkeSendeRevurderingTilAttestering {
    object FantIkkeRevurdering : KunneIkkeSendeRevurderingTilAttestering()
    object FantIkkeAktørId : KunneIkkeSendeRevurderingTilAttestering()
    object KunneIkkeOppretteOppgave : KunneIkkeSendeRevurderingTilAttestering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeSendeRevurderingTilAttestering()
}

sealed class KunneIkkeIverksetteRevurdering {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteRevurdering()
    object KunneIkkeUtbetale : KunneIkkeIverksetteRevurdering()
    object KunneIkkeKontrollsimulere : KunneIkkeIverksetteRevurdering()
    object FantIkkeRevurdering : KunneIkkeIverksetteRevurdering()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeIverksetteRevurdering()
}

sealed class KunneIkkeLageBrevutkastForRevurdering {
    object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForRevurdering()
    object FantIkkePerson : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForRevurdering()
}

sealed class KunneIkkeUnderkjenneRevurdering {
    object FantIkkeRevurdering : KunneIkkeUnderkjenneRevurdering()
    object FantIkkeAktørId : KunneIkkeUnderkjenneRevurdering()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeUnderkjenneRevurdering()

    object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenneRevurdering()
}

sealed class KunneIkkeLeggeTilGrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlag()
    object UgyldigStatus : KunneIkkeLeggeTilGrunnlag()
}

sealed class KunneIkkeHenteGrunnlag {
    object FantIkkeBehandling : KunneIkkeHenteGrunnlag()
}

data class LeggTilUføregrunnlagResponse(
    val revurdering: Revurdering,
    val simulertEndringGrunnlag: GrunnlagService.SimulertEndringGrunnlag
)
