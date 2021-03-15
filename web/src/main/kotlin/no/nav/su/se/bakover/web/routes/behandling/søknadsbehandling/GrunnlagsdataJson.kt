package no.nav.su.se.bakover.web.routes.behandling.søknadsbehandling

import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Uføregrunnlag
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson.Companion.toJson

internal data class GrunnlagsdataJson(
    val uføre: List<UføregrunnlagJson> = emptyList(),
) {
    data class UføregrunnlagJson(
        val id: String,
        val opprettet: String,
        val periode: PeriodeJson,
        val uføregrad: Int,
        val forventetInntekt: Int,
    )
}

private fun Uføregrunnlag.toJson() = GrunnlagsdataJson.UføregrunnlagJson(
    id = this.id.toString(),
    opprettet = this.opprettet.toString(),
    periode = this.periode.toJson(),
    uføregrad = this.uføregrad.value,
    forventetInntekt = this.forventetInntekt,
)

private fun List<Uføregrunnlag>.toJson() = this.map {
    it.toJson()
}

internal fun Grunnlagsdata.toJson() = GrunnlagsdataJson(
    uføre = this.uføregrunnlag.toJson()
)
