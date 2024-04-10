package no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import vilkår.opplysningsplikt.domain.KunneIkkeLageOpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktBeskrivelse
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt
import java.time.Clock
import java.util.UUID

internal data class OpplysningspliktVilkårJson(
    val vurderinger: List<VurderingsperiodeOpplysningspliktVilkårJson>,
)

internal enum class OpplysningspliktBeskrivelseJson {
    TilstrekkeligDokumentasjon,
    UtilstrekkeligDokumentasjon,
}

internal data class VurderingsperiodeOpplysningspliktVilkårJson(
    val periode: PeriodeJson,
    val beskrivelse: OpplysningspliktBeskrivelseJson,
) {
    fun toDomain(
        clock: Clock,
    ): Either<KunneIkkeLageOpplysningspliktVilkår.Vurderingsperiode, VurderingsperiodeOpplysningsplikt> {
        return VurderingsperiodeOpplysningsplikt.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            vurderingsperiode = periode.toPeriode(),
            grunnlag = Opplysningspliktgrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = periode.toPeriode(),
                beskrivelse = when (beskrivelse) {
                    OpplysningspliktBeskrivelseJson.TilstrekkeligDokumentasjon -> {
                        OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon
                    }

                    OpplysningspliktBeskrivelseJson.UtilstrekkeligDokumentasjon -> {
                        OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon
                    }
                },
            ),
        )
    }
}

internal fun List<VurderingsperiodeOpplysningspliktVilkårJson>.toDomain(
    clock: Clock,
): Either<KunneIkkeLeggeTilOpplysningsplikt, OpplysningspliktVilkår.Vurdert> {
    val vurderingsperioder = this.map { json ->
        json.toDomain(clock).getOrElse {
            return KunneIkkeLeggeTilOpplysningsplikt.UgyldigOpplysningspliktVilkår(it).left()
        }
    }
    return OpplysningspliktVilkår.Vurdert.tryCreate(vurderingsperioder.toNonEmptyList())
        .mapLeft { KunneIkkeLeggeTilOpplysningsplikt.UgyldigOpplysningspliktVilkår(it) }
}

internal fun OpplysningspliktVilkår.toJson(): OpplysningspliktVilkårJson? {
    return when (this) {
        OpplysningspliktVilkår.IkkeVurdert -> {
            null
        }

        is OpplysningspliktVilkår.Vurdert -> {
            this.toJson()
        }
    }
}

internal fun OpplysningspliktVilkår.Vurdert.toJson(): OpplysningspliktVilkårJson {
    return OpplysningspliktVilkårJson(
        vurderinger = vurderingsperioder.map { it.toJson() },
    )
}

internal fun VurderingsperiodeOpplysningsplikt.toJson(): VurderingsperiodeOpplysningspliktVilkårJson {
    return VurderingsperiodeOpplysningspliktVilkårJson(
        periode = periode.toJson(),
        beskrivelse = grunnlag.beskrivelse.toJson(),
    )
}

internal fun OpplysningspliktBeskrivelse.toJson(): OpplysningspliktBeskrivelseJson {
    return when (this) {
        OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon -> {
            OpplysningspliktBeskrivelseJson.TilstrekkeligDokumentasjon
        }

        OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon -> {
            OpplysningspliktBeskrivelseJson.UtilstrekkeligDokumentasjon
        }
    }
}
