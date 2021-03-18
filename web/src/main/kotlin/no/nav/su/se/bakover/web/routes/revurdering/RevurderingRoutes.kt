package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.routing.Route
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.routes.sak.sakPath

internal const val revurderingPath = "$sakPath/{sakId}/revurderinger"

@KtorExperimentalAPI
internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService
) {
    opprettRevurderingRoute(revurderingService)

    oppdaterRevurderingsperiodeRoute(revurderingService)

    beregnOgSimulerRevurdering(revurderingService)

    sendRevurderingTilAttestering(revurderingService)

    iverksettRevurderingRoute(revurderingService)

    brevutkastForRevurdering(revurderingService)

    underkjennRevurdering(revurderingService)

    leggTilGrunnlagRevurderingRoutes(revurderingService)
}
