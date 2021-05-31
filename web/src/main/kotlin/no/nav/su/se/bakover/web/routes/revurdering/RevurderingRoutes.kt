package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.routing.Route
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.routes.sak.sakPath

internal const val revurderingPath = "$sakPath/{sakId}/revurderinger"

internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService,
) {
    opprettRevurderingRoute(revurderingService)

    oppdaterRevurderingRoute(revurderingService)

    beregnOgSimulerRevurdering(revurderingService)

    forhåndsvarslingRoute(revurderingService)

    sendRevurderingTilAttestering(revurderingService)

    underkjennRevurdering(revurderingService)

    iverksettRevurderingRoute(revurderingService)

    brevutkastForRevurdering(revurderingService)

    fortsettEtterForhåndsvarselRoute(revurderingService)

    leggTilGrunnlagRevurderingRoutes(revurderingService)

    leggTilFradragRevurdering(revurderingService)

    hentGrunnlagRevurderingRoutes(revurderingService)
}
