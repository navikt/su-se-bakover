package no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.sequence
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageOpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
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
    fun toDomain(clock: Clock): Either<KunneIkkeLageOpplysningspliktVilkår.Vurderingsperiode, VurderingsperiodeOpplysningsplikt> {
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

internal fun List<VurderingsperiodeOpplysningspliktVilkårJson>.toDomain(clock: Clock): Either<KunneIkkeLeggeTilOpplysningsplikt, OpplysningspliktVilkår.Vurdert> {
    return map { it.toDomain(clock) }.sequence()
        .mapLeft { KunneIkkeLeggeTilOpplysningsplikt.UgyldigOpplysningspliktVilkår(it) }
        .flatMap { vurderingsperioder ->
            OpplysningspliktVilkår.Vurdert.tryCreate(
                vurderingsperioder.toNonEmptyList(),

            )
                .mapLeft { KunneIkkeLeggeTilOpplysningsplikt.UgyldigOpplysningspliktVilkår(it) }
        }
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
