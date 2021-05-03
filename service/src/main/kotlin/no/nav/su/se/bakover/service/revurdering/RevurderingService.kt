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
    fun hentRevurdering(revurderingId: UUID): Revurdering?

    fun opprettRevurdering(
        opprettRevurderingRequest: OpprettRevurderingRequest,
    ): Either<KunneIkkeOppretteRevurdering, Revurdering>

    fun oppdaterRevurdering(
        oppdaterRevurderingRequest: OppdaterRevurderingRequest,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering>

    fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradrag: List<Fradrag>,
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, Revurdering>

    fun forhåndsvarsleEllerSendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        revurderingshandling: Revurderingshandling,
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering>

    fun lagBrevutkastForForhåndsvarsling(
        revurderingId: UUID,
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray>

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

    fun fortsettEtterForhåndsvarsling(
        request: FortsettEtterForhåndsvarslingRequest,
    ): Either<FortsettEtterForhåndsvarselFeil, Revurdering>

    fun leggTilUføregrunnlag(revurderingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>): Either<KunneIkkeLeggeTilGrunnlag, LeggTilUføregrunnlagResponse>
    fun hentUføregrunnlag(revurderingId: UUID): Either<KunneIkkeHenteGrunnlag, GrunnlagService.SimulerEndretGrunnlagsdata>
}

sealed class FortsettEtterForhåndsvarslingRequest {
    abstract val revurderingId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract val begrunnelse: String

    data class FortsettMedSammeOpplysninger(
        override val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val begrunnelse: String,
        val fritekstTilBrev: String,
    ) : FortsettEtterForhåndsvarslingRequest()

    data class FortsettMedAndreOpplysninger(
        override val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val begrunnelse: String,
    ) : FortsettEtterForhåndsvarslingRequest()

    data class AvsluttUtenEndringer(
        override val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val begrunnelse: String,
        val fritekstTilBrev: String,
    ) : FortsettEtterForhåndsvarslingRequest()
}

sealed class FortsettEtterForhåndsvarselFeil {
    object FantIkkeRevurdering : FortsettEtterForhåndsvarselFeil()
    object RevurderingErIkkeIRiktigTilstand : FortsettEtterForhåndsvarselFeil()
    object RevurderingErIkkeForhåndsvarslet : FortsettEtterForhåndsvarselFeil()
    object AlleredeBesluttet : FortsettEtterForhåndsvarselFeil()
    data class Attestering(val subError: KunneIkkeSendeRevurderingTilAttestering) : FortsettEtterForhåndsvarselFeil()
}

object FantIkkeRevurdering

data class SendTilAttesteringRequest(
    val revurderingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
)

enum class Revurderingshandling {
    SEND_TIL_ATTESTERING,
    FORHÅNDSVARSLE,
}

sealed class KunneIkkeOppretteRevurdering {
    object FantIkkeSak : KunneIkkeOppretteRevurdering()
    object FantIngentingSomKanRevurderes : KunneIkkeOppretteRevurdering()
    object FantIkkeAktørId : KunneIkkeOppretteRevurdering()
    object KunneIkkeOppretteOppgave : KunneIkkeOppretteRevurdering()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeOppretteRevurdering()
    object UgyldigÅrsak : KunneIkkeOppretteRevurdering()
    object UgyldigBegrunnelse : KunneIkkeOppretteRevurdering()
    object PeriodeOgÅrsakKombinasjonErUgyldig : KunneIkkeOppretteRevurdering()
}

sealed class KunneIkkeOppdatereRevurdering {
    object FantIkkeRevurdering : KunneIkkeOppdatereRevurdering()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeOppdatereRevurdering()
    object UgyldigÅrsak : KunneIkkeOppdatereRevurdering()
    object UgyldigBegrunnelse : KunneIkkeOppdatereRevurdering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeOppdatereRevurdering()
    object KanIkkeOppdatereRevurderingSomErForhåndsvarslet : KunneIkkeOppdatereRevurdering()
    object PeriodeOgÅrsakKombinasjonErUgyldig : KunneIkkeOppdatereRevurdering()
}

sealed class KunneIkkeBeregneOgSimulereRevurdering {
    object MåSendeGrunnbeløpReguleringSomÅrsakSammenMedForventetInntekt : KunneIkkeBeregneOgSimulereRevurdering()
    object FantIkkeRevurdering : KunneIkkeBeregneOgSimulereRevurdering()
    object SimuleringFeilet : KunneIkkeBeregneOgSimulereRevurdering()
    object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneOgSimulereRevurdering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeBeregneOgSimulereRevurdering()

    object UfullstendigBehandlingsinformasjon : KunneIkkeBeregneOgSimulereRevurdering()
    object UfullstendigVilkårsvurdering : KunneIkkeBeregneOgSimulereRevurdering()
}

sealed class KunneIkkeForhåndsvarsle {
    object AlleredeForhåndsvarslet : KunneIkkeForhåndsvarsle()
    object FantIkkeRevurdering : KunneIkkeForhåndsvarsle()
    object FantIkkeAktørId : KunneIkkeForhåndsvarsle()
    object FantIkkePerson : KunneIkkeForhåndsvarsle()
    object KunneIkkeJournalføre : KunneIkkeForhåndsvarsle()
    object KunneIkkeDistribuere : KunneIkkeForhåndsvarsle()
    object KunneIkkeOppretteOppgave : KunneIkkeForhåndsvarsle()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeForhåndsvarsle()

    data class Attestering(val subError: KunneIkkeSendeRevurderingTilAttestering) : KunneIkkeForhåndsvarsle()
}

sealed class KunneIkkeSendeRevurderingTilAttestering {
    object FantIkkeRevurdering : KunneIkkeSendeRevurderingTilAttestering()
    object FantIkkeAktørId : KunneIkkeSendeRevurderingTilAttestering()
    object KunneIkkeOppretteOppgave : KunneIkkeSendeRevurderingTilAttestering()
    object KanIkkeRegulereGrunnbeløpTilOpphør : KunneIkkeSendeRevurderingTilAttestering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeSendeRevurderingTilAttestering()

    object ManglerBeslutningPåForhåndsvarsel : KunneIkkeSendeRevurderingTilAttestering()
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

sealed class KunneIkkeLeggeTilGrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlag()
    object UgyldigStatus : KunneIkkeLeggeTilGrunnlag()
}

sealed class KunneIkkeHenteGrunnlag {
    object FantIkkeBehandling : KunneIkkeHenteGrunnlag()
}

data class LeggTilUføregrunnlagResponse(
    val revurdering: Revurdering,
    val simulerEndretGrunnlagsdata: GrunnlagService.SimulerEndretGrunnlagsdata,
)
