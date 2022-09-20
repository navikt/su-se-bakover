package no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.sequence
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageOpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.test.fixedTidspunkt
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
    fun toDomain(): Either<KunneIkkeLageOpplysningspliktVilkår.Vurderingsperiode, VurderingsperiodeOpplysningsplikt> {
        return VurderingsperiodeOpplysningsplikt.tryCreate(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            vurderingsperiode = periode.toPeriode(),
            grunnlag = Opplysningspliktgrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
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

internal fun List<VurderingsperiodeOpplysningspliktVilkårJson>.toDomain(): Either<KunneIkkeLeggeTilOpplysningsplikt, OpplysningspliktVilkår.Vurdert> {
    return map { it.toDomain() }.sequence()
        .mapLeft { KunneIkkeLeggeTilOpplysningsplikt.UgyldigOpplysningspliktVilkår(it) }
        .flatMap { vurderingsperioder ->
            OpplysningspliktVilkår.Vurdert.tryCreate(NonEmptyList.fromListUnsafe(vurderingsperioder))
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
