package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseService
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.routes.revurdering.avslutt.avsluttRevurderingRoute
import no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel.forhåndsvarslingRoute
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.leggTilLovligOppholdRoute
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.leggTilUtlandsoppholdRoute
import java.time.Clock

internal const val revurderingPath = "$sakPath/{sakId}/revurderinger"

internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService,
    stansAvYtelseService: StansYtelseService,
    gjenopptakAvYtelseService: GjenopptaYtelseService,
    sakService: SakService,
    clock: Clock,
    satsFactory: SatsFactory,
) {
    opprettRevurderingRoute(revurderingService, satsFactory)

    oppdaterRevurderingRoute(revurderingService, satsFactory)

    beregnOgSimulerRevurdering(revurderingService, satsFactory)

    oppdaterTilbakekrevingsbehandlingRoute(revurderingService, satsFactory)

    forhåndsvarslingRoute(revurderingService, satsFactory)

    sendRevurderingTilAttestering(revurderingService, satsFactory)

    underkjennRevurdering(revurderingService, satsFactory, clock)

    iverksettRevurderingRoute(revurderingService, satsFactory)

    brevutkastForRevurdering(revurderingService)

    leggTilGrunnlagRevurderingRoutes(revurderingService, satsFactory)

    leggTilUtlandsoppholdRoute(revurderingService, satsFactory)

    leggTilFradragRevurdering(revurderingService, clock, satsFactory)

    LeggTilBosituasjonRevurderingRoute(revurderingService, satsFactory)

    leggTilFormueRevurderingRoute(revurderingService, satsFactory, clock)

    hentGrunnlagRevurderingRoutes(sakService, satsFactory)

    stansUtbetaling(stansAvYtelseService, satsFactory)

    gjenopptaUtbetaling(gjenopptakAvYtelseService, satsFactory)

    avsluttRevurderingRoute(revurderingService, satsFactory)

    pensjonsVilkårRoutes(revurderingService, satsFactory, clock)

    leggTilLovligOppholdRoute(revurderingService, satsFactory)

    flyktningVilkårRoutes(revurderingService, satsFactory, clock)

    fastOppholdVilkårRoutes(revurderingService, satsFactory, clock)

    personligOppmøteVilkårRoutes(revurderingService, satsFactory, clock)

    institusjonsoppholdRoutes(revurderingService, satsFactory, clock)
}
