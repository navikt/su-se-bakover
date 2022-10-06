package no.nav.su.se.bakover.web.routes.vilkår.fastopphold

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFastOppholdINorge
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.test.fixedTidspunkt
import java.util.UUID

internal fun List<LeggTilVurderingsperiodeFastOppholdJson>.toDomain(): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, FastOppholdINorgeVilkår.Vurdert> {
    return map { it.toDomain() }
        .let { vurderingsperioder ->
            FastOppholdINorgeVilkår.Vurdert.tryCreate(
                vurderingsperioder.toNonEmptyList(),

            )
                .mapLeft { KunneIkkeLeggeFastOppholdINorgeVilkår.UgyldigFastOppholdINorgeVikår(it) }
        }
}

internal fun KunneIkkeLeggeFastOppholdINorgeVilkår.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeFastOppholdINorgeVilkår.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }
        is KunneIkkeLeggeFastOppholdINorgeVilkår.Revurdering -> {
            when (val feil = this.feil) {
                Revurdering.KunneIkkeLeggeTilFastOppholdINorgeVilkår.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.heleBehandlingsperiodenMåHaVurderinger
                }
                is Revurdering.KunneIkkeLeggeTilFastOppholdINorgeVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
                Revurdering.KunneIkkeLeggeTilFastOppholdINorgeVilkår.AlleVurderingsperioderMåHaSammeResultat -> {
                    Feilresponser.alleVurderingsperioderMåHaSammeResultat
                }
            }
        }
        is KunneIkkeLeggeFastOppholdINorgeVilkår.Søknadsbehandling -> {
            when (val feil = this.feil) {
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
            }
        }
        is KunneIkkeLeggeFastOppholdINorgeVilkår.UgyldigFastOppholdINorgeVikår -> {
            when (this.feil) {
                FastOppholdINorgeVilkår.Vurdert.UgyldigFastOppholdINorgeVikår.OverlappendeVurderingsperioder -> {
                    Feilresponser.overlappendeVurderingsperioder
                }
            }
        }
    }
}

internal data class LeggTilVurderingsperiodeFastOppholdJson(
    val periode: PeriodeJson,
    val vurdering: FastOppholdINorgeVurderingJson,
) {
    fun toDomain(): VurderingsperiodeFastOppholdINorge {
        return VurderingsperiodeFastOppholdINorge.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            vurdering = vurdering.toDomain(),
            periode = periode.toPeriode(),
        )
    }
}

enum class FastOppholdINorgeVurderingJson {
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

internal data class FastOppholdINorgeVilkårJson(
    val vurderinger: List<VurderingsperiodeFastOppholdINorgeJson>,
    val resultat: String,
)

internal data class VurderingsperiodeFastOppholdINorgeJson(
    val resultat: String,
    val periode: PeriodeJson,
)

internal fun FastOppholdINorgeVilkår.toJson(): FastOppholdINorgeVilkårJson? {
    return when (this) {
        FastOppholdINorgeVilkår.IkkeVurdert -> {
            null
        }
        is FastOppholdINorgeVilkår.Vurdert -> {
            this.toJson()
        }
    }
}

internal fun FastOppholdINorgeVilkår.Vurdert.toJson(): FastOppholdINorgeVilkårJson {
    return FastOppholdINorgeVilkårJson(
        vurderinger = vurderingsperioder.map { it.toJson() },
        resultat = vurdering.toJson(),
    )
}

internal fun VurderingsperiodeFastOppholdINorge.toJson(): VurderingsperiodeFastOppholdINorgeJson {
    return VurderingsperiodeFastOppholdINorgeJson(
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
