package no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import common.presentation.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import vilkår.common.domain.Vurdering
import vilkår.opplysningsplikt.domain.OpplysningspliktBeskrivelse
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt
import java.time.Clock
import java.util.UUID

data class OpplysningspliktVilkårJson(
    val resultat: OpplysningspliktBeskrivelseJson,
    val vurderinger: List<VurderingsperiodeOpplysningspliktVilkårJson>,
)

enum class OpplysningspliktBeskrivelseJson {
    TilstrekkeligDokumentasjon,
    UtilstrekkeligDokumentasjon,
}

data class VurderingsperiodeOpplysningspliktVilkårJson(
    val periode: PeriodeJson,
    val beskrivelse: OpplysningspliktBeskrivelseJson,
) {
    fun toDomain(
        clock: Clock,
    ): Either<Resultat, VurderingsperiodeOpplysningsplikt> {
        val validertPeriode = periode.toPeriodeOrResultat().getOrElse { return it.left() }
        return VurderingsperiodeOpplysningsplikt.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            vurderingsperiode = validertPeriode,
            grunnlag = Opplysningspliktgrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = validertPeriode,
                beskrivelse = when (beskrivelse) {
                    OpplysningspliktBeskrivelseJson.TilstrekkeligDokumentasjon -> {
                        OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon
                    }

                    OpplysningspliktBeskrivelseJson.UtilstrekkeligDokumentasjon -> {
                        OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon
                    }
                },
            ),
        ).mapLeft { it.tilResultat() }
    }
}

fun List<VurderingsperiodeOpplysningspliktVilkårJson>.toDomain(
    clock: Clock,
): Either<Resultat, OpplysningspliktVilkår.Vurdert> {
    if (isEmpty()) {
        return KunneIkkeLeggeTilOpplysningsplikt.ManglerVurderingsperioder.tilResultat().left()
    }
    val vurderingsperioder = this.map { json ->
        json.toDomain(clock).getOrElse {
            return it.left()
        }
    }
    return OpplysningspliktVilkår.Vurdert.tryCreate(vurderingsperioder.toNonEmptyList())
        .mapLeft { it.tilResultat() }
}

fun OpplysningspliktVilkår.toJson(): OpplysningspliktVilkårJson? {
    return when (this) {
        OpplysningspliktVilkår.IkkeVurdert -> {
            null
        }

        is OpplysningspliktVilkår.Vurdert -> {
            this.toJson()
        }
    }
}

fun OpplysningspliktVilkår.Vurdert.toJson(): OpplysningspliktVilkårJson {
    return OpplysningspliktVilkårJson(
        resultat = when (vurdering) {
            Vurdering.Avslag,
            Vurdering.Uavklart,
            -> OpplysningspliktBeskrivelseJson.UtilstrekkeligDokumentasjon

            Vurdering.Innvilget -> OpplysningspliktBeskrivelseJson.TilstrekkeligDokumentasjon
        },
        vurderinger = vurderingsperioder.map { it.toJson() },
    )
}

fun VurderingsperiodeOpplysningsplikt.toJson(): VurderingsperiodeOpplysningspliktVilkårJson {
    return VurderingsperiodeOpplysningspliktVilkårJson(
        periode = periode.toJson(),
        beskrivelse = grunnlag.beskrivelse.toJson(),
    )
}

fun OpplysningspliktBeskrivelse.toJson(): OpplysningspliktBeskrivelseJson {
    return when (this) {
        OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon -> {
            OpplysningspliktBeskrivelseJson.TilstrekkeligDokumentasjon
        }

        OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon -> {
            OpplysningspliktBeskrivelseJson.UtilstrekkeligDokumentasjon
        }
    }
}
