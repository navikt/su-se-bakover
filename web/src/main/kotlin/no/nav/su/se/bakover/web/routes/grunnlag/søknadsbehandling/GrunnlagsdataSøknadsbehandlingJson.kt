package no.nav.su.se.bakover.web.routes.grunnlag.søknadsbehandling

import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.toJson

internal data class GrunnlagsdataSøknadsbehandlingJson(
    val uføre: UføregrunnlagJson? = null,
)

internal fun Grunnlagsdata.toSøknadsbehandlingJson() = GrunnlagsdataSøknadsbehandlingJson(
    uføre = this.uføregrunnlag.firstOrNull()?.toJson(),
)
