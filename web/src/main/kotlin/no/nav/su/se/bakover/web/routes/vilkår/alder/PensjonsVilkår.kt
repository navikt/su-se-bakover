package no.nav.su.se.bakover.web.routes.vilkår.alder

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.sequence
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLagePensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePensjon
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.web.routes.Feilresponser
import java.util.UUID

internal enum class PensjonsVilkårResultat {
    VilkårOppfylt,
    VilkårIkkeOppfylt,
    HarAlderssakTilBehandling;

    fun toResultat(): Resultat {
        return when (this) {
            VilkårOppfylt -> Resultat.Innvilget
            VilkårIkkeOppfylt -> Resultat.Avslag
            HarAlderssakTilBehandling -> Resultat.Uavklart
        }
    }
}

internal data class PensjonsVilkårJson(
    val vurderinger: List<VurderingsperiodePensjonsVilkårJson>,
    val resultat: PensjonsVilkårResultat,
)

internal data class VurderingsperiodePensjonsVilkårJson(
    val periode: PeriodeJson,
    val resultat: PensjonsVilkårResultat,
) {
    fun toDomain(): Either<KunneIkkeLagePensjonsVilkår.Vurderingsperiode, VurderingsperiodePensjon> {
        return VurderingsperiodePensjon.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            vurderingsperiode = periode.toPeriode(),
            grunnlag = null,
            resultat = resultat.toResultat(),
        )
    }
}

internal fun List<VurderingsperiodePensjonsVilkårJson>.toDomain(): Either<KunneIkkeLeggeTilPensjonsVilkår, PensjonsVilkår.Vurdert> {
    return map { it.toDomain() }.sequence()
        .mapLeft { KunneIkkeLeggeTilPensjonsVilkår.UgyldigPensjonsVilkår(it) }
        .flatMap { vurderingsperioder ->
            PensjonsVilkår.Vurdert.tryCreate(NonEmptyList.fromListUnsafe(vurderingsperioder))
                .mapLeft { KunneIkkeLeggeTilPensjonsVilkår.UgyldigPensjonsVilkår(it) }
        }
}

internal fun PensjonsVilkår.toJson(): PensjonsVilkårJson? {
    return when (this) {
        PensjonsVilkår.IkkeVurdert -> {
            null
        }
        is PensjonsVilkår.Vurdert -> {
            this.toJson()
        }
    }
}

internal fun PensjonsVilkår.Vurdert.toJson(): PensjonsVilkårJson {
    return PensjonsVilkårJson(
        vurderinger = vurderingsperioder.map { it.toJson() },
        resultat = resultat.toPensjonsVilkårResultat(),
    )
}

internal fun VurderingsperiodePensjon.toJson(): VurderingsperiodePensjonsVilkårJson {
    return VurderingsperiodePensjonsVilkårJson(
        periode = periode.toJson(),
        resultat = resultat.toPensjonsVilkårResultat(),
    )
}

internal fun Resultat.toPensjonsVilkårResultat(): PensjonsVilkårResultat {
    return when (this) {
        Resultat.Avslag -> PensjonsVilkårResultat.VilkårIkkeOppfylt
        Resultat.Innvilget -> PensjonsVilkårResultat.VilkårOppfylt
        Resultat.Uavklart -> PensjonsVilkårResultat.HarAlderssakTilBehandling
    }
}

internal fun KunneIkkeLeggeTilPensjonsVilkår.tilResultat(): no.nav.su.se.bakover.web.Resultat {
    return when (this) {
        is KunneIkkeLeggeTilPensjonsVilkår.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }
        is KunneIkkeLeggeTilPensjonsVilkår.UgyldigPensjonsVilkår -> {
            when (this.feil) {
                KunneIkkeLagePensjonsVilkår.OverlappendeVurderingsperioder -> {
                    Feilresponser.overlappendeVurderingsperioder
                }
                KunneIkkeLagePensjonsVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                    Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
                }
            }
        }
        is KunneIkkeLeggeTilPensjonsVilkår.Revurdering -> {
            when (val feil = this.feil) {
                is Revurdering.KunneIkkeLeggeTilPensjonsVilkår.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.vilkårMåVurderesForHeleBehandlingsperioden
                }
                is Revurdering.KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
                is Revurdering.KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder -> {
                    Feilresponser.vilkårKunRelevantForAlder
                }
            }
        }
        is KunneIkkeLeggeTilPensjonsVilkår.Søknadsbehandling -> {
            when (val feil = this.feil) {
                is Søknadsbehandling.KunneIkkeLeggeTilPensjonsVilkår.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.vilkårMåVurderesForHeleBehandlingsperioden
                }
                is Søknadsbehandling.KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
                is Søknadsbehandling.KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder -> {
                    Feilresponser.vilkårKunRelevantForAlder
                }
            }
        }
    }
}
