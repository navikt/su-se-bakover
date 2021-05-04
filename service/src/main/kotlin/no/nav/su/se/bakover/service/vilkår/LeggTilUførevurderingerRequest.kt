package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.util.UUID

data class LeggTilUførevurderingerRequest(
    /** Dekker både søknadsbehandlingId og revurderingId */
    val behandlingId: UUID,
    // TODO jah: Bytt til NEL
    val vurderinger: List<LeggTilUførevurderingRequest>,
) {
    sealed class UgyldigUførevurdering {
        object UføregradOgForventetInntektMangler : UgyldigUførevurdering()
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUførevurdering()
        object OverlappendeVurderingsperioder : UgyldigUførevurdering()
        object VurderingsperiodeMangler : UgyldigUførevurdering()
    }

    fun toVilkår(): Either<UgyldigUførevurdering, Vilkår.Vurdert.Uførhet> {
        return vurderinger.map { request ->
            request.toVurderingsperiode().getOrHandle {
                return when (it) {
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.VurderingsperiodeMangler -> UgyldigUførevurdering.VurderingsperiodeMangler.left()
                }
            }
        }.let { vurderingsperioder ->
            Vilkår.Vurdert.Uførhet.tryCreate(vurderingsperioder)
                .mapLeft {
                    when (it) {
                        Vilkår.Vurdert.Uførhet.UgyldigUførevilkår.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder
                        Vilkår.Vurdert.Uførhet.UgyldigUførevilkår.VurderingsperioderMangler -> UgyldigUførevurdering.VurderingsperiodeMangler
                    }
                }
        }
    }
}
