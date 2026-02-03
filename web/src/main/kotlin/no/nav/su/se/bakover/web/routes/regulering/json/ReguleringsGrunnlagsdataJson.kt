package no.nav.su.se.bakover.web.routes.regulering.json

import no.nav.su.se.bakover.domain.regulering.ReguleringGrunnlagsdata
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragResponseJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragResponseJson.Companion.toJson

internal data class ReguleringsGrunnlagsdataJson(
    val uføreFraGjeldendeVedtak: List<UføregrunnlagJson>,
    val fradragFraGjeldendeVedtak: List<FradragResponseJson>,

    val uføreUnderRegulering: List<UføregrunnlagJson>?,
    val fradragUnderRegulering: List<FradragResponseJson>?,
) {
    companion object {
        fun ReguleringGrunnlagsdata.toJson() = ReguleringsGrunnlagsdataJson(
            uføreFraGjeldendeVedtak = uføreFraGjeldendeVedtak.map { it.toJson() },
            fradragFraGjeldendeVedtak = fradragFraGjeldendeVedtak.map { it.toJson() },
            uføreUnderRegulering = uføreUnderRegulering?.map { it.toJson() },
            fradragUnderRegulering = fradragUnderRegulering?.map { it.toJson() },
        )
    }
}
