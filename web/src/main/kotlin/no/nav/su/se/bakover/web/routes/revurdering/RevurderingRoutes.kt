package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseService
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.web.routes.revurdering.avslutt.avsluttRevurderingRoute
import no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel.forhåndsvarslingRoute
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.leggTilLovligOppholdRoute
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.leggTilUtlandsoppholdRoute
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal const val REVURDERING_PATH = "$SAK_PATH/{sakId}/revurderinger"

internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService,
    stansAvYtelseService: StansYtelseService,
    gjenopptakAvYtelseService: GjenopptaYtelseService,
    sakService: SakService,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
) {
    opprettRevurderingRoute(revurderingService, formuegrenserFactory)

    oppdaterRevurderingRoute(revurderingService, formuegrenserFactory)

    beregnOgSimulerRevurdering(revurderingService, formuegrenserFactory)

    oppdaterTilbakekrevingsbehandlingRoute(revurderingService, formuegrenserFactory)

    forhåndsvarslingRoute(revurderingService, formuegrenserFactory)

    sendRevurderingTilAttestering(revurderingService, formuegrenserFactory)

    underkjennRevurdering(revurderingService, formuegrenserFactory, clock)

    iverksettRevurderingRoute(revurderingService, formuegrenserFactory)

    brevutkastForRevurdering(revurderingService)

    leggTilGrunnlagRevurderingRoutes(revurderingService, formuegrenserFactory)

    leggTilUtlandsoppholdRoute(revurderingService, formuegrenserFactory)

    leggTilFradragRevurdering(revurderingService, clock, formuegrenserFactory)

    leggTilGrunnlagBosituasjonRoutes(revurderingService, formuegrenserFactory)

    leggTilFormueRevurderingRoute(revurderingService, formuegrenserFactory, clock)

    hentGrunnlagRevurderingRoutes(sakService, formuegrenserFactory)

    stansUtbetaling(stansAvYtelseService, formuegrenserFactory)

    gjenopptaUtbetaling(gjenopptakAvYtelseService, formuegrenserFactory)

    avsluttRevurderingRoute(revurderingService, formuegrenserFactory)

    pensjonsVilkårRoutes(revurderingService, formuegrenserFactory, clock)

    leggTilLovligOppholdRoute(revurderingService, formuegrenserFactory)

    flyktningVilkårRoutes(revurderingService, formuegrenserFactory, clock)

    fastOppholdVilkårRoutes(revurderingService, formuegrenserFactory, clock)

    personligOppmøteVilkårRoutes(revurderingService, formuegrenserFactory, clock)

    institusjonsoppholdRoutes(revurderingService, formuegrenserFactory, clock)
}
