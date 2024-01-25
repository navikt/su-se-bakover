package no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.heleBehandlingsperiodenMåHaVurderinger
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.overlappendeVurderingsperioder
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.VurderingInstitusjonsoppholdJson.Companion.toJson
import vilkår.domain.Vurdering
import java.time.Clock

internal data class LeggTilVurderingsperiodeInstitusjonsoppholdJson(
    val vurderingsperioder: List<VurderingsperiodeInstitusjonsoppholdJson>,
) {
    fun toDomain(clock: Clock): Either<Resultat, InstitusjonsoppholdVilkår.Vurdert> {
        if (vurderingsperioder.isEmpty()) return heleBehandlingsperiodenMåHaVurderinger.left()
        return InstitusjonsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = vurderingsperioder.map {
                VurderingsperiodeInstitusjonsopphold.create(
                    opprettet = Tidspunkt.now(clock),
                    vurdering = it.vurdering.toDomain(),
                    periode = it.periode.toPeriode(),
                )
            }.toNonEmptyList(),
        ).mapLeft {
            when (it) {
                InstitusjonsoppholdVilkår.Vurdert.UgyldigInstitisjonsoppholdVilkår.OverlappendeVurderingsperioder -> overlappendeVurderingsperioder
            }
        }
    }
}

internal data class VurderingsperiodeInstitusjonsoppholdJson(
    val periode: PeriodeJson,
    val vurdering: VurderingInstitusjonsoppholdJson,
)

enum class VurderingInstitusjonsoppholdJson {
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

    companion object {
        fun Vurdering.toJson(): VurderingInstitusjonsoppholdJson {
            return when (this) {
                Vurdering.Innvilget -> VilkårOppfylt
                Vurdering.Avslag -> VilkårIkkeOppfylt
                Vurdering.Uavklart -> Uavklart
            }
        }
    }
}

internal data class InstitusjonsoppholdJson(
    val resultat: VurderingInstitusjonsoppholdJson,
    val vurderingsperioder: List<VurderingsperiodeInstitusjonsoppholdJson>,
) {
    companion object {
        fun InstitusjonsoppholdVilkår.toJson(): InstitusjonsoppholdJson? = when (this) {
            InstitusjonsoppholdVilkår.IkkeVurdert -> null
            is InstitusjonsoppholdVilkår.Vurdert -> InstitusjonsoppholdJson(
                resultat = this.vurdering.toJson(),
                vurderingsperioder = this.vurderingsperioder.map {
                    VurderingsperiodeInstitusjonsoppholdJson(
                        periode = it.periode.toJson(),
                        vurdering = it.vurdering.toJson(),
                    )
                },
            )
        }
    }
}
