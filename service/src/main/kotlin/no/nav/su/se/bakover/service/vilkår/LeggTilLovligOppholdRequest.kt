package no.nav.su.se.bakover.service.vilkår

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageLovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeLovligOpphold
import java.time.Clock
import java.util.UUID

enum class LovligOppholdVilkårStatus {
    VilkårOppfylt, VilkårIkkeOppfylt, Uavklart;

    fun toResultat(): Resultat {
        return when (this) {
            VilkårOppfylt -> Resultat.Innvilget
            VilkårIkkeOppfylt -> Resultat.Avslag
            Uavklart -> Resultat.Uavklart
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
        vurderingsperioder = NonEmptyList.fromListUnsafe(toVurderingsperiode(clock)),
    )

    private fun toVurderingsperiode(
        clock: Clock,
    ) = vurderinger.map {
        VurderingsperiodeLovligOpphold.tryCreate(
            opprettet = Tidspunkt.now(clock),
            resultat = it.status.toResultat(),
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
