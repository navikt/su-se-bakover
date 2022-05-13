package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import java.time.Clock
import java.util.UUID

data class LeggTilFlereUtenlandsoppholdRequest(
    val behandlingId: UUID,
    val request: Nel<LeggTilUtenlandsoppholdRequest>,
) {
    sealed class UgyldigUtenlandsopphold {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUtenlandsopphold()
        object OverlappendeVurderingsperioder : UgyldigUtenlandsopphold()
    }

    fun tilVilkår(clock: Clock): Either<UgyldigUtenlandsopphold, UtenlandsoppholdVilkår.Vurdert> {
        return UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder =
            request.map {
                it.tilVurderingsperiode(
                    clock = clock,
                ).getOrHandle { feil ->
                    return when (feil) {
                        LeggTilUtenlandsoppholdRequest.UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                    }
                }
            },
        ).getOrHandle {
            return when (it) {
                UtenlandsoppholdVilkår.Vurdert.UgyldigUtenlandsoppholdVilkår.OverlappendeVurderingsperioder -> UgyldigUtenlandsopphold.OverlappendeVurderingsperioder.left()
            }
        }.right()
    }
}

enum class UtenlandsoppholdStatus {
    SkalVæreMerEnn90DagerIUtlandet,
    SkalHoldeSegINorge,
    Uavklart
}

data class LeggTilUtenlandsoppholdRequest(
    /** Dekker både søknadsbehandlingId og revurderingId */
    val behandlingId: UUID,
    val periode: Periode,
    val status: UtenlandsoppholdStatus,
    val begrunnelse: String?,
) {
    sealed class UgyldigUtenlandsopphold {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUtenlandsopphold()
    }

    fun tilVurderingsperiode(clock: Clock): Either<UgyldigUtenlandsopphold, VurderingsperiodeUtenlandsopphold> {
        return when (status) {
            UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet -> {
                lagVurderingsperiode(
                    resultat = Resultat.Avslag,
                    clock = clock,
                ).getOrHandle {
                    return it.left()
                }
            }

            UtenlandsoppholdStatus.SkalHoldeSegINorge -> {
                lagVurderingsperiode(
                    resultat = Resultat.Innvilget,
                    clock = clock,
                ).getOrHandle {
                    return it.left()
                }
            }

            UtenlandsoppholdStatus.Uavklart -> {
                lagVurderingsperiode(
                    resultat = Resultat.Uavklart,
                    clock = clock,
                ).getOrHandle {
                    return it.left()
                }
            }
        }.right()
    }

    private fun lagVurderingsperiode(
        resultat: Resultat,
        clock: Clock,
    ): Either<UgyldigUtenlandsopphold, VurderingsperiodeUtenlandsopphold> {
        return VurderingsperiodeUtenlandsopphold.tryCreate(
            opprettet = Tidspunkt.now(clock),
            resultat = resultat,
            grunnlag = null,
            vurderingsperiode = periode,
        ).getOrHandle {
            return when (it) {
                VurderingsperiodeUtenlandsopphold.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }
        }.right()
    }
}
