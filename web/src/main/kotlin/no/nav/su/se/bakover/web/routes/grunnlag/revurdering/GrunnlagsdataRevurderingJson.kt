package no.nav.su.se.bakover.web.routes.grunnlag.revurdering

import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.toJson

internal data class SimulertEndringGrunnlagJson(
    /** Sammensmelting av vedtakene før revurderingen.*/
    val førBehandling: GrunnlagsdataRevurderingJson,
    /** De endringene som er lagt til i revurderingen */
    val endring: GrunnlagsdataRevurderingJson,
    /** Sammensmeltinga av førBehandling og endring  */
    val resultat: GrunnlagsdataRevurderingJson,
)

internal fun GrunnlagService.SimulerEndretGrunnlagsdata.toJson() = SimulertEndringGrunnlagJson(
    førBehandling = førBehandling.toRevurderingJson(),
    endring = endring.toRevurderingJson(),
    resultat = resultat.toRevurderingJson(),
)

internal data class GrunnlagsdataRevurderingJson(
    val uføre: List<UføregrunnlagJson> = emptyList(),
)

internal fun Grunnlagsdata.toRevurderingJson() = GrunnlagsdataRevurderingJson(
    uføre = this.uføregrunnlag.map { it.toJson() },
)
