package no.nav.su.se.bakover.domain.vilkår.lovligopphold

import no.nav.su.se.bakover.behandling.BehandlingsId
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.Vurdering
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.lovligopphold.domain.VurderingsperiodeLovligOpphold
import java.time.Clock

enum class LovligOppholdVilkårStatus {
    VilkårOppfylt,
    VilkårIkkeOppfylt,
    Uavklart,
    ;

    fun toResultat(): Vurdering {
        return when (this) {
            VilkårOppfylt -> Vurdering.Innvilget
            VilkårIkkeOppfylt -> Vurdering.Avslag
            Uavklart -> Vurdering.Uavklart
        }
    }
}

data class LovligOppholdVurderinger(
    val periode: Periode,
    val status: LovligOppholdVilkårStatus,
)

data class LeggTilLovligOppholdRequest(
    val behandlingId: BehandlingsId,
    val vurderinger: List<LovligOppholdVurderinger>,
) {

    fun toVilkår(
        clock: Clock,
    ) = LovligOppholdVilkår.Vurdert.tryCreate(
        vurderingsperioder = toVurderingsperiode(clock).toNonEmptyList(),
    )

    private fun toVurderingsperiode(
        clock: Clock,
    ) = vurderinger.map {
        VurderingsperiodeLovligOpphold.tryCreate(
            opprettet = Tidspunkt.now(clock),
            vurdering = it.status.toResultat(),
            vurderingsperiode = it.periode,
        )
    }
}
