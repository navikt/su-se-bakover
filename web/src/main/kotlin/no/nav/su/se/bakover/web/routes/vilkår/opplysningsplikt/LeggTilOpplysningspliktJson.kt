package no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.sequence
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageOpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilOpplysningsplikt
import java.util.UUID

internal data class OpplysningspliktVilkårJson(
    val periode: PeriodeJson,
    val beskrivelse: OpplysningspliktBeskrivelseJson,
) {
    fun toDomain(): Either<KunneIkkeLageOpplysningspliktVilkår.Vurderingsperiode, VurderingsperiodeOpplysningsplikt> {
        return VurderingsperiodeOpplysningsplikt.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            vurderingsperiode = periode.toPeriode(),
            grunnlag = Opplysningspliktgrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
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

    internal enum class OpplysningspliktBeskrivelseJson {
        TilstrekkeligDokumentasjon,
        UtilstrekkeligDokumentasjon,
    }
}

internal fun List<OpplysningspliktVilkårJson>.toDomain(): Either<KunneIkkeLeggeTilOpplysningsplikt, OpplysningspliktVilkår.Vurdert> {
    return map { it.toDomain() }.sequence()
        .mapLeft { KunneIkkeLeggeTilOpplysningsplikt.UgyldigOpplysningspliktVilkår(it) }
        .flatMap { vurderingsperioder ->
            OpplysningspliktVilkår.Vurdert.tryCreate(NonEmptyList.fromListUnsafe(vurderingsperioder))
                .mapLeft { KunneIkkeLeggeTilOpplysningsplikt.UgyldigOpplysningspliktVilkår(it) }
        }
}
