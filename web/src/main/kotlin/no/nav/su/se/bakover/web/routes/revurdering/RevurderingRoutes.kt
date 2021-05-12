package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.routing.Route
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.routes.sak.sakPath
import java.time.Clock

internal const val revurderingPath = "$sakPath/{sakId}/revurderinger"

@KtorExperimentalAPI
internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService,
    clock: Clock,
) {
    opprettRevurderingRoute(revurderingService)

    oppdaterRevurderingRoute(revurderingService)

    beregnOgSimulerRevurdering(revurderingService, clock)

    forhåndsvarslingRoute(revurderingService)

    sendRevurderingTilAttestering(revurderingService)

    underkjennRevurdering(revurderingService)

    iverksettRevurderingRoute(revurderingService)

    brevutkastForRevurdering(revurderingService)

    fortsettEtterForhåndsvarselRoute(revurderingService)

    leggTilGrunnlagRevurderingRoutes(revurderingService)

    hentGrunnlagRevurderingRoutes(revurderingService)
}
