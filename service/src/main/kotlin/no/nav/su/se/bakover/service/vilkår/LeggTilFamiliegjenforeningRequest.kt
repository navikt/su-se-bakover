package no.nav.su.se.bakover.service.vilkår

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFamiliegjenforening
import java.time.Clock
import java.util.UUID

enum class FamiliegjenforeningvilkårStatus {
    VilkårOppfylt, VilkårIkkeOppfylt, Uavklart;
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
        if (stønadsperiode == null)
            throw IllegalArgumentException("Stønadsperiode er ikke lagt i søknadsbehandling for å legge til familiegjenforening vilkår. id $behandlingId")
        else
            FamiliegjenforeningVilkår.Vurdert.create(
                vurderingsperioder = NonEmptyList.fromListUnsafe(toVurderingsperiode(clock, stønadsperiode)),
            )

    private fun toVurderingsperiode(
        clock: Clock,
        stønadsperiode: Periode,
    ) = vurderinger.map {
        VurderingsperiodeFamiliegjenforening.create(
            opprettet = Tidspunkt.now(clock),
            resultat = when (it.status) {
                FamiliegjenforeningvilkårStatus.VilkårOppfylt -> Resultat.Innvilget
                FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt -> Resultat.Avslag
                FamiliegjenforeningvilkårStatus.Uavklart -> Resultat.Uavklart
            },
            periode = stønadsperiode,
        )
    }
}
