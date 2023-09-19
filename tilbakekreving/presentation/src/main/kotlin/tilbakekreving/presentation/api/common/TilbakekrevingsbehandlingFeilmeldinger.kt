package tilbakekreving.presentation.api.common

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.errorJson

internal val ingenÅpneKravgrunnlag = HttpStatusCode.NotFound.errorJson(
    "Ingen ferdig behandlede kravgrunnlag",
    "ingen_ferdig_behandlede_kravgrunnlag",
)

// TODO jah: flytt til person infra/presentation
internal val ikkeTilgangTilSak = HttpStatusCode.NotFound.errorJson(
    "Ikke tilgang til sak",
    "ikke_tilgang_til_sak",
)
