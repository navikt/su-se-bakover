package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOppholdIUtlandet
import java.time.Clock
import java.util.UUID

data class LeggTilOppholdIUtlandetRevurderingRequest(
    val behandlingId: UUID,
    val request: Nel<LeggTilOppholdIUtlandetRequest>
) {
    sealed class UgyldigOppholdIUtlandet {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigOppholdIUtlandet()
        object OverlappendeVurderingsperioder : UgyldigOppholdIUtlandet()
    }

    fun toVilkår(clock: Clock): Either<UgyldigOppholdIUtlandet, OppholdIUtlandetVilkår.Vurdert> {
        return OppholdIUtlandetVilkår.Vurdert.tryCreate(
            vurderingsperioder =
            request.map {
                it.toVilkår(
                    clock = clock,
                ).getOrHandle {
                    return when (it) {
                        LeggTilOppholdIUtlandetRequest.UgyldigOppholdIUtlandet.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigOppholdIUtlandet.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                    }
                }
            },
        ).getOrHandle {
            return when (it) {
                OppholdIUtlandetVilkår.Vurdert.UgyldigOppholdIUtlandetVilkår.OverlappendeVurderingsperioder -> UgyldigOppholdIUtlandet.OverlappendeVurderingsperioder.left()
            }
        }.right()
    }
}

data class LeggTilOppholdIUtlandetRequest(
    /** Dekker både søknadsbehandlingId og revurderingId */
    val behandlingId: UUID,
    val periode: Periode,
    val status: Status,
    val begrunnelse: String?,
) {
    sealed class UgyldigOppholdIUtlandet {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigOppholdIUtlandet()
    }

    enum class Status {
        SkalVæreMerEnn90DagerIUtlandet,
        SkalHoldeSegINorge,
        Uavklart
    }

    fun toVilkår(clock: Clock): Either<UgyldigOppholdIUtlandet, VurderingsperiodeOppholdIUtlandet> {
        return when (status) {
            Status.SkalVæreMerEnn90DagerIUtlandet -> {
                lagVilkår(
                    resultat = Resultat.Avslag,
                    clock = clock,
                ).getOrHandle {
                    return it.left()
                }
            }

            Status.SkalHoldeSegINorge -> {
                lagVilkår(
                    resultat = Resultat.Innvilget,
                    clock = clock,
                ).getOrHandle {
                    return it.left()
                }
            }

            Status.Uavklart -> {
                lagVilkår(
                    resultat = Resultat.Uavklart,
                    clock = clock,
                ).getOrHandle {
                    return it.left()
                }
            }
        }.right()
    }

    private fun lagVilkår(
        resultat: Resultat,
        clock: Clock,
    ): Either<UgyldigOppholdIUtlandet, VurderingsperiodeOppholdIUtlandet> {
        return VurderingsperiodeOppholdIUtlandet.tryCreate(
            opprettet = Tidspunkt.now(clock),
            resultat = resultat,
            grunnlag = null,
            vurderingsperiode = periode,
            begrunnelse = begrunnelse,
        ).getOrHandle {
            return when (it) {
                VurderingsperiodeOppholdIUtlandet.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigOppholdIUtlandet.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }
        }.right()
    }
}
