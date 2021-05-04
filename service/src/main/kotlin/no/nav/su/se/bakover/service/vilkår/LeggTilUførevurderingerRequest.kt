package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
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
    }

    fun toVilkår(): Either<UgyldigUførevurdering, Vilkår.Vurdert.Uførhet> {
        return vurderinger.map { request ->
            request.toVurderingsperiode().getOrHandle {
                return when (it) {
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                }
            }
        }.let {
            Vilkår.Vurdert.Uførhet(it).right()
        }
    }
}
