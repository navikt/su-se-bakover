package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOppholdIUtlandet
import java.time.Clock
import java.util.UUID

data class LeggTilOppholdIUtlandetRequest(
    /** Dekker både søknadsbehandlingId og revurderingId */
    val behandlingId: UUID,
    val status: Status,
    val begrunnelse: String?,
) {
    sealed class UgyldigOppholdIUtlandet {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigOppholdIUtlandet()
        object OverlappendeVurderingsperioder : UgyldigOppholdIUtlandet()
    }

    enum class Status {
        SkalVæreMerEnn90DagerIUtlandet,
        SkalHoldeSegINorge,
        Uavklart
    }

    fun toVilkår(behandlingsperiode: Periode, clock: Clock): Either<UgyldigOppholdIUtlandet, OppholdIUtlandetVilkår.Vurdert> {
        return when (status) {
            Status.SkalVæreMerEnn90DagerIUtlandet -> {
                lagVurdertVilkår(
                    resultat = Resultat.Avslag,
                    clock = clock,
                    periode = behandlingsperiode,
                ).getOrHandle {
                    return it.left()
                }
            }

            Status.SkalHoldeSegINorge -> {
                lagVurdertVilkår(
                    resultat = Resultat.Innvilget,
                    clock = clock,
                    periode = behandlingsperiode,
                ).getOrHandle {
                    return it.left()
                }
            }

            Status.Uavklart -> {
                lagVurdertVilkår(
                    resultat = Resultat.Uavklart,
                    clock = clock,
                    periode = behandlingsperiode,
                ).getOrHandle {
                    return it.left()
                }
            }
        }.right()
    }

    private fun lagVurdertVilkår(
        resultat: Resultat,
        clock: Clock,
        periode: Periode,
    ): Either<UgyldigOppholdIUtlandet, OppholdIUtlandetVilkår.Vurdert> {
        return OppholdIUtlandetVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeOppholdIUtlandet.tryCreate(
                    opprettet = Tidspunkt.now(clock),
                    resultat = resultat,
                    grunnlag = null,
                    vurderingsperiode = periode,
                    begrunnelse = begrunnelse,
                ).getOrHandle {
                    return when (it) {
                        VurderingsperiodeOppholdIUtlandet.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigOppholdIUtlandet.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                    }
                },
            ),
        ).getOrHandle {
            return when (it) {
                OppholdIUtlandetVilkår.Vurdert.UgyldigOppholdIUtlandetVilkår.OverlappendeVurderingsperioder -> UgyldigOppholdIUtlandet.OverlappendeVurderingsperioder.left()
            }
        }.right()
    }
}
