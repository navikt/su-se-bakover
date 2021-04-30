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
    object UføregradOgForventetInntektMangler

    fun toVilkår(): Either<UføregradOgForventetInntektMangler, Vilkår.Vurdert.Uførhet> {
        return vurderinger.map {
            it.toVurderingsperiode().getOrHandle {
                return UføregradOgForventetInntektMangler.left()
            }
        }.let {
            Vilkår.Vurdert.Uførhet(it).right()
        }
    }
}
