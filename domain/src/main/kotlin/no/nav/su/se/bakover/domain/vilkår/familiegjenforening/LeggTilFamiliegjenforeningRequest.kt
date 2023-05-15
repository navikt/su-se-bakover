package no.nav.su.se.bakover.domain.vilkår.familiegjenforening

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFamiliegjenforening
import java.time.Clock
import java.util.UUID

enum class FamiliegjenforeningvilkårStatus {
    VilkårOppfylt, VilkårIkkeOppfylt, Uavklart
}

data class FamiliegjenforeningVurderinger(
    val status: FamiliegjenforeningvilkårStatus,
)

data class LeggTilFamiliegjenforeningRequest(
    val behandlingId: UUID,
    val vurderinger: List<FamiliegjenforeningVurderinger>,
) {
    fun toVilkår(
        clock: Clock,
        stønadsperiode: Periode?,
    ) =
        if (stønadsperiode == null) {
            throw IllegalArgumentException("Stønadsperiode er ikke lagt i søknadsbehandling for å legge til familiegjenforening vilkår. id $behandlingId")
        } else {
            FamiliegjenforeningVilkår.Vurdert.create(
                vurderingsperioder = toVurderingsperiode(clock, stønadsperiode).toNonEmptyList(),
            )
        }

    private fun toVurderingsperiode(
        clock: Clock,
        stønadsperiode: Periode,
    ) = vurderinger.map {
        VurderingsperiodeFamiliegjenforening.create(
            opprettet = Tidspunkt.now(clock),
            vurdering = when (it.status) {
                FamiliegjenforeningvilkårStatus.VilkårOppfylt -> Vurdering.Innvilget
                FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt -> Vurdering.Avslag
                FamiliegjenforeningvilkårStatus.Uavklart -> Vurdering.Uavklart
            },
            periode = stønadsperiode,
        )
    }
}
