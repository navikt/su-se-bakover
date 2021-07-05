package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.routing.Route
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.routes.sak.sakPath
import java.time.Clock

internal const val revurderingPath = "$sakPath/{sakId}/revurderinger"

internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService,
    vedtakService: VedtakService,
    clock: Clock
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

    leggTilFradragRevurdering(revurderingService, clock)

    LeggTilBosituasjonRevurderingRoute(revurderingService)

    leggTilFormueRevurderingRoute(revurderingService)

    hentGrunnlagRevurderingRoutes(revurderingService, vedtakService)
}
