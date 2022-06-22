package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.sequence
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
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
            Uavklart -> Resultat.Avslag
        }
    }
}

data class LovligOppholdVurderinger(
    val status: LovligOppholdVilkårStatus,
)

data class LeggTilLovligOppholdRequest(
    val behandlingId: UUID,
    val vurderinger: List<LovligOppholdVurderinger>,
) {

    fun toVilkår(
        stønadsperiode: Stønadsperiode?,
        clock: Clock,
    ): Either<KunneIkkeLageLovligOppholdVilkår, LovligOppholdVilkår.Vurdert> = if (stønadsperiode == null)
        throw IllegalArgumentException("Stønadsperiode er ikke lagt i søknadsbehandling for å legge til familiegjenforening vilkår. id $behandlingId")
    else
        toVurderingsperiode(stønadsperiode, clock).mapLeft {
            return it.left()
        }.map {
            return LovligOppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = NonEmptyList.fromListUnsafe(it),
            )
        }

    private fun toVurderingsperiode(
        stønadsperiode: Stønadsperiode,
        clock: Clock,
    ): Either<KunneIkkeLageLovligOppholdVilkår, List<VurderingsperiodeLovligOpphold>> = vurderinger.map {
        VurderingsperiodeLovligOpphold.tryCreate(
            opprettet = Tidspunkt.now(clock),
            resultat = it.status.toResultat(),
            vurderingsperiode = stønadsperiode.periode,
            grunnlag = LovligOppholdGrunnlag.tryCreate(
                opprettet = Tidspunkt.now(),
                periode = stønadsperiode.periode,
            ),
        )
    }.sequence().getOrHandle {
        return it.left()
    }.right()
}
