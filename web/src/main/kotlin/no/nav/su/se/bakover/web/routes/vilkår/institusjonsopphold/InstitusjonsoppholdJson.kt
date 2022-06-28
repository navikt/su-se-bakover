package no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold

import arrow.core.Either
import arrow.core.Nel
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.Feilresponser.heleBehandlingsperiodenMåHaVurderinger
import no.nav.su.se.bakover.web.routes.Feilresponser.overlappendeVurderingsperioder
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.VurderingInstitusjonsoppholdJson.Companion.toJson
import java.time.Clock

internal data class LeggTilVurderingsperiodeInstitusjonsoppholdJson(
    val vurderingsperioder: List<VurderingsperiodeInstitusjonsoppholdJson>,
) {
    fun toDomain(clock: Clock): Either<Resultat, InstitusjonsoppholdVilkår.Vurdert> {
        if (vurderingsperioder.isEmpty()) return heleBehandlingsperiodenMåHaVurderinger.left()
        return InstitusjonsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = Nel.fromListUnsafe(
                vurderingsperioder.map {
                    VurderingsperiodeInstitusjonsopphold.create(
                        opprettet = Tidspunkt.now(clock),
                        vurdering = it.vurdering.toDomain(),
                        periode = it.periode.toPeriode(),
                    )
                },
            ),
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
    Uavklart;

    fun toDomain(): no.nav.su.se.bakover.domain.vilkår.Vurdering {
        return when (this) {
            VilkårOppfylt -> no.nav.su.se.bakover.domain.vilkår.Vurdering.Innvilget
            VilkårIkkeOppfylt -> no.nav.su.se.bakover.domain.vilkår.Vurdering.Avslag
            Uavklart -> no.nav.su.se.bakover.domain.vilkår.Vurdering.Uavklart
        }
    }

    companion object {
        fun no.nav.su.se.bakover.domain.vilkår.Vurdering.toJson(): VurderingInstitusjonsoppholdJson {
            return when (this) {
                no.nav.su.se.bakover.domain.vilkår.Vurdering.Innvilget -> VilkårOppfylt
                no.nav.su.se.bakover.domain.vilkår.Vurdering.Avslag -> VilkårIkkeOppfylt
                no.nav.su.se.bakover.domain.vilkår.Vurdering.Uavklart -> Uavklart
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
