package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.util.UUID

data class LeggTilUførevurderingerRequest(
    /** Dekker både søknadsbehandlingId og revurderingId */
    val behandlingId: UUID,
    val vurderinger: Nel<LeggTilUførevurderingRequest>,
) {
    sealed class UgyldigUførevurdering {
        object UføregradOgForventetInntektMangler : UgyldigUførevurdering()
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUførevurdering()
        object OverlappendeVurderingsperioder : UgyldigUførevurdering()
        object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : UgyldigUførevurdering()
    }

    fun toVilkår(behandlingsperiode: Periode): Either<UgyldigUførevurdering, Vilkår.Vurdert.Uførhet> {
        return vurderinger.map { request ->
            request.toVurderingsperiode(behandlingsperiode).getOrHandle {
                return when (it) {
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden.left()
                }
            }
        }.let { vurderingsperioder ->
            Vilkår.Vurdert.Uførhet.tryCreate(Nel.fromListUnsafe(vurderingsperioder))
                .mapLeft {
                    when (it) {
                        Vilkår.Vurdert.Uførhet.UgyldigUførevilkår.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder
                    }
                }
        }
    }
}
