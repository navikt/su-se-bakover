package no.nav.su.se.bakover.domain.vilkår.familiegjenforening

import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.Vurdering
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.familiegjenforening.domain.VurderingsperiodeFamiliegjenforening
import java.time.Clock

enum class FamiliegjenforeningvilkårStatus {
    VilkårOppfylt,
    VilkårIkkeOppfylt,
    Uavklart,
}

data class FamiliegjenforeningVurderinger(
    val periode: Periode,
    val status: FamiliegjenforeningvilkårStatus,
)

data class LeggTilFamiliegjenforeningRequest(
    val behandlingId: BehandlingsId,
    val vurderinger: List<FamiliegjenforeningVurderinger>,
) {
    fun toVilkår(clock: Clock) = FamiliegjenforeningVilkår.Vurdert.create(
        vurderingsperioder = toVurderingsperiode(clock).toNonEmptyList(),
    )

    private fun toVurderingsperiode(clock: Clock) = vurderinger.map {
        VurderingsperiodeFamiliegjenforening.create(
            opprettet = Tidspunkt.now(clock),
            vurdering = when (it.status) {
                FamiliegjenforeningvilkårStatus.VilkårOppfylt -> Vurdering.Innvilget
                FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt -> Vurdering.Avslag
                FamiliegjenforeningvilkårStatus.Uavklart -> Vurdering.Uavklart
            },
            periode = it.periode,
        )
    }
}
