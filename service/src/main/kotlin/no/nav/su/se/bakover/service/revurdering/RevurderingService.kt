package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import java.util.UUID
import kotlin.reflect.KClass

interface RevurderingService {
    fun hentRevurdering(revurderingId: UUID): Revurdering?

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

    fun forhåndsvarsle(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String
    ): Either<KunneIkkeForhåndsvarsle, Revurdering>

    fun sendTilAttestering(
        request: SendTilAttesteringRequest,
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
}

data class SendTilAttesteringRequest(
    val revurderingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean
)

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

sealed class KunneIkkeForhåndsvarsle {
    object UgyldigStatus : KunneIkkeForhåndsvarsle()
    object FantIkkeAktørId : KunneIkkeForhåndsvarsle()
    object KunneIkkeJournalføre : KunneIkkeForhåndsvarsle()
    object KunneIkkeDistribuere : KunneIkkeForhåndsvarsle()
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
    object KunneIkkeJournaleføreBrev : KunneIkkeIverksetteRevurdering()
    object KunneIkkeDistribuereBrev : KunneIkkeIverksetteRevurdering()
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
