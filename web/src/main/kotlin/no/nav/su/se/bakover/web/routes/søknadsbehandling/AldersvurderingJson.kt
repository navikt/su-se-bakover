package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata

data class AldersvurderingJson(
    val harSaksbehandlerAvgjort: Boolean,
    val maskinellVurderingsresultat: String,
) {
    companion object {
        fun Aldersvurdering.toJson(): AldersvurderingJson {
            return when (this) {
                is Aldersvurdering.Historisk -> AldersvurderingJson(
                    harSaksbehandlerAvgjort = false,
                    maskinellVurderingsresultat = MaskinellVurderingsresultat.HISTORISK.toString(),
                )

                is Aldersvurdering.SkalIkkeVurderes -> AldersvurderingJson(
                    harSaksbehandlerAvgjort = false,
                    maskinellVurderingsresultat = MaskinellVurderingsresultat.SKAL_IKKE_VURDERES.toString(),
                )

                is Aldersvurdering.Vurdert -> {
                    AldersvurderingJson(
                        harSaksbehandlerAvgjort = this.saksbehandlersAvgjørelse != null,
                        maskinellVurderingsresultat = when (this.maskinellVurdering) {
                            is MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre -> MaskinellVurderingsresultat.IKKE_RETT_PÅ_UFØRE
                            is MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre -> MaskinellVurderingsresultat.RETT_PÅ_UFØRE
                            is MaskinellAldersvurderingMedGrunnlagsdata.Ukjent -> MaskinellVurderingsresultat.UKJENT
                            is MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPaaAlder -> MaskinellVurderingsresultat.IKKE_RETT_PÅ_ALDER
                            is MaskinellAldersvurderingMedGrunnlagsdata.RettPaaAlderSU -> MaskinellVurderingsresultat.RETT_PÅ_ALDER
                        }.toString(),
                    )
                }
            }
        }
    }
}

internal enum class MaskinellVurderingsresultat {
    IKKE_RETT_PÅ_UFØRE,
    RETT_PÅ_UFØRE,
    UKJENT,
    HISTORISK,
    SKAL_IKKE_VURDERES,
    RETT_PÅ_ALDER,
    IKKE_RETT_PÅ_ALDER,
}
