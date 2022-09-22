package no.nav.su.se.bakover.web.routes.vilkår.flyktning

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFlyktning
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.Feilresponser
import java.util.UUID

internal fun List<LeggTilVurderingsperiodeFlyktningVilkårJson>.toDomain(): Either<KunneIkkeLeggeTilFlyktningVilkår, FlyktningVilkår.Vurdert> {
    return map { it.toDomain() }
        .let { vurderingsperioder ->
            FlyktningVilkår.Vurdert.tryCreate(
                vurderingsperioder.toNonEmptyList(),

            )
                .mapLeft { KunneIkkeLeggeTilFlyktningVilkår.UgyldigFlyktningVilkår(it) }
        }
}

internal fun KunneIkkeLeggeTilFlyktningVilkår.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilFlyktningVilkår.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }
        is KunneIkkeLeggeTilFlyktningVilkår.Revurdering -> {
            when (val feil = this.feil) {
                Revurdering.KunneIkkeLeggeTilFlyktningVilkår.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.heleBehandlingsperiodenMåHaVurderinger
                }
                is Revurdering.KunneIkkeLeggeTilFlyktningVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
                Revurdering.KunneIkkeLeggeTilFlyktningVilkår.VilkårKunRelevantForUføre -> {
                    Feilresponser.vilkårKunRelevantForUføre
                }
            }
        }
        is KunneIkkeLeggeTilFlyktningVilkår.Søknadsbehandling -> {
            when (val feil = this.feil) {
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
            }
        }
        is KunneIkkeLeggeTilFlyktningVilkår.UgyldigFlyktningVilkår -> {
            when (this.feil) {
                FlyktningVilkår.Vurdert.UgyldigFlyktningVilkår.OverlappendeVurderingsperioder -> {
                    Feilresponser.overlappendeVurderingsperioder
                }
            }
        }
    }
}

internal data class LeggTilVurderingsperiodeFlyktningVilkårJson(
    val periode: PeriodeJson,
    val vurdering: FlyktningVurderingJson,
) {
    fun toDomain(): VurderingsperiodeFlyktning {
        return VurderingsperiodeFlyktning.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            vurdering = vurdering.toDomain(),
            periode = periode.toPeriode(),
        )
    }
}

enum class FlyktningVurderingJson {
    VilkårOppfylt,
    VilkårIkkeOppfylt,
    Uavklart,
    ;

    fun toDomain(): Vurdering {
        return when (this) {
            VilkårOppfylt -> Vurdering.Innvilget
            VilkårIkkeOppfylt -> Vurdering.Avslag
            Uavklart -> Vurdering.Uavklart
        }
    }
}

internal data class FlyktningVilkårJson(
    val vurderinger: List<VurderingsperiodeFlyktningVilkårJson>,
    val resultat: String,
)

internal data class VurderingsperiodeFlyktningVilkårJson(
    val resultat: String,
    val periode: PeriodeJson,
)

internal fun FlyktningVilkår.toJson(): FlyktningVilkårJson? {
    return when (this) {
        FlyktningVilkår.IkkeVurdert -> {
            null
        }
        is FlyktningVilkår.Vurdert -> {
            this.toJson()
        }
    }
}

internal fun FlyktningVilkår.Vurdert.toJson(): FlyktningVilkårJson {
    return FlyktningVilkårJson(
        vurderinger = vurderingsperioder.map { it.toJson() },
        resultat = vurdering.toJson(),
    )
}

internal fun VurderingsperiodeFlyktning.toJson(): VurderingsperiodeFlyktningVilkårJson {
    return VurderingsperiodeFlyktningVilkårJson(
        resultat = vurdering.toJson(),
        periode = periode.toJson(),
    )
}

private fun Vurdering.toJson(): String {
    return when (this) {
        Vurdering.Avslag -> "VilkårIkkeOppfylt"
        Vurdering.Innvilget -> "VilkårOppfylt"
        Vurdering.Uavklart -> "Uavklart"
    }
}
