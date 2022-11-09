package no.nav.su.se.bakover.domain.vilkår.lovligopphold

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageLovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeLovligOpphold
import java.time.Clock
import java.util.UUID

enum class LovligOppholdVilkårStatus {
    VilkårOppfylt, VilkårIkkeOppfylt, Uavklart;

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
    val behandlingId: UUID,
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

sealed interface KunneIkkeLeggetilLovligOppholdVilkår {
    object FantIkkeBehandling : KunneIkkeLeggetilLovligOppholdVilkår

    data class UgyldigLovligOppholdVilkår(val feil: KunneIkkeLageLovligOppholdVilkår) :
        KunneIkkeLeggetilLovligOppholdVilkår

    data class FeilVedSøknadsbehandling(val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold) :
        KunneIkkeLeggetilLovligOppholdVilkår
}
