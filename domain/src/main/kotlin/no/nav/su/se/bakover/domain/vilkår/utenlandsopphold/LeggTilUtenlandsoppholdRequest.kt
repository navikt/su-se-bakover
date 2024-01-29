package no.nav.su.se.bakover.domain.vilkår.utenlandsopphold

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.VurderingsperiodeUtenlandsopphold
import vilkår.common.domain.Vurdering
import java.time.Clock
import java.util.UUID

data class LeggTilFlereUtenlandsoppholdRequest(
    val behandlingId: UUID,
    val request: Nel<LeggTilUtenlandsoppholdRequest>,
) {
    sealed interface UgyldigUtenlandsopphold {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUtenlandsopphold
        data object OverlappendeVurderingsperioder : UgyldigUtenlandsopphold
    }

    fun tilVilkår(clock: Clock): Either<UgyldigUtenlandsopphold, UtenlandsoppholdVilkår.Vurdert> {
        return UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder =
            request.map {
                it.tilVurderingsperiode(
                    clock = clock,
                ).getOrElse { feil ->
                    return when (feil) {
                        LeggTilUtenlandsoppholdRequest.UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                    }
                }
            },
        ).getOrElse {
            return when (it) {
                UtenlandsoppholdVilkår.Vurdert.UgyldigUtenlandsoppholdVilkår.OverlappendeVurderingsperioder -> UgyldigUtenlandsopphold.OverlappendeVurderingsperioder.left()
            }
        }.right()
    }
}

enum class UtenlandsoppholdStatus {
    SkalVæreMerEnn90DagerIUtlandet,
    SkalHoldeSegINorge,
    Uavklart,
}

data class LeggTilUtenlandsoppholdRequest(
    /** Dekker både søknadsbehandlingId og revurderingId */
    val behandlingId: UUID,
    val periode: Periode,
    val status: UtenlandsoppholdStatus,
) {
    sealed interface UgyldigUtenlandsopphold {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUtenlandsopphold
    }

    fun tilVurderingsperiode(clock: Clock): Either<UgyldigUtenlandsopphold, VurderingsperiodeUtenlandsopphold> {
        return when (status) {
            UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet -> {
                lagVurderingsperiode(
                    vurdering = Vurdering.Avslag,
                    clock = clock,
                ).getOrElse {
                    return it.left()
                }
            }

            UtenlandsoppholdStatus.SkalHoldeSegINorge -> {
                lagVurderingsperiode(
                    vurdering = Vurdering.Innvilget,
                    clock = clock,
                ).getOrElse {
                    return it.left()
                }
            }

            UtenlandsoppholdStatus.Uavklart -> {
                lagVurderingsperiode(
                    vurdering = Vurdering.Uavklart,
                    clock = clock,
                ).getOrElse {
                    return it.left()
                }
            }
        }.right()
    }

    private fun lagVurderingsperiode(
        vurdering: Vurdering,
        clock: Clock,
    ): Either<UgyldigUtenlandsopphold, VurderingsperiodeUtenlandsopphold> {
        return VurderingsperiodeUtenlandsopphold.tryCreate(
            opprettet = Tidspunkt.now(clock),
            vurdering = vurdering,
            grunnlag = null,
            vurderingsperiode = periode,
        ).getOrElse {
            return when (it) {
                VurderingsperiodeUtenlandsopphold.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }
        }.right()
    }
}
