package no.nav.su.se.bakover.service.vilkår

import arrow.core.nonEmptyListOf
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

data class LeggTilFamiliegjenforeningRequest(
    val behandlingId: UUID,
    val status: FamiliegjenforeningvilkårStatus,
) {

    fun toVilkår(
        periode: Periode,
        clock: Clock,
    ) = FamiliegjenforeningVilkår.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(toVurderingsperiode(periode, clock)),
    )

    private fun toVurderingsperiode(
        vurderingsperiode: Periode,
        clock: Clock,
    ) = VurderingsperiodeFamiliegjenforening.create(
        opprettet = Tidspunkt.now(clock),
        resultat = when (status) {
            FamiliegjenforeningvilkårStatus.VilkårOppfylt -> Resultat.Innvilget
            FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt -> Resultat.Avslag
            FamiliegjenforeningvilkårStatus.Uavklart -> Resultat.Uavklart
        },
        periode = vurderingsperiode,
    )
}
