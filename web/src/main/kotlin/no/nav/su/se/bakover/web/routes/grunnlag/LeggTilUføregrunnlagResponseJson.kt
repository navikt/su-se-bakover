package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.service.revurdering.LeggTilUføregrunnlagResponse
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingJson
import no.nav.su.se.bakover.web.routes.revurdering.toJson

internal data class LeggTilUføregrunnlagResponseJson(
    val revurdering: RevurderingJson,
    val simulertEndringGrunnlag: SimulertEndringGrunnlagJson
)

internal fun LeggTilUføregrunnlagResponse.toJson() = LeggTilUføregrunnlagResponseJson(
    revurdering = revurdering.toJson(),
    simulertEndringGrunnlag = simulerEndretGrunnlagsdata.toJson()
)
