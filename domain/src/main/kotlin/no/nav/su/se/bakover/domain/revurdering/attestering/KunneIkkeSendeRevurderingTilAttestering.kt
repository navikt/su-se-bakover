package no.nav.su.se.bakover.domain.revurdering.attestering

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.RevurderingsutfallSomIkkeStøttes
import kotlin.reflect.KClass

sealed interface KunneIkkeSendeRevurderingTilAttestering {
    data class FeilInnvilget(
        val feil: SimulertRevurdering.KunneIkkeSendeInnvilgetRevurderingTilAttestering,
    ) : KunneIkkeSendeRevurderingTilAttestering

    data class FeilOpphørt(
        val feil: SimulertRevurdering.Opphørt.KanIkkeSendeOpphørtRevurderingTilAttestering,
    ) : KunneIkkeSendeRevurderingTilAttestering

    data object FantIkkeRevurdering : KunneIkkeSendeRevurderingTilAttestering
    data object FantIkkeAktørId : KunneIkkeSendeRevurderingTilAttestering
    data object KunneIkkeOppretteOppgave : KunneIkkeSendeRevurderingTilAttestering
    data object KanIkkeRegulereGrunnbeløpTilOpphør : KunneIkkeSendeRevurderingTilAttestering
    data object ManglerFritekstTilVedtaksbrev : KunneIkkeSendeRevurderingTilAttestering
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeSendeRevurderingTilAttestering

    data object FeilutbetalingStøttesIkke : KunneIkkeSendeRevurderingTilAttestering
    data class RevurderingsutfallStøttesIkke(
        val feilmeldinger: List<RevurderingsutfallSomIkkeStøttes>,
    ) : KunneIkkeSendeRevurderingTilAttestering
}
