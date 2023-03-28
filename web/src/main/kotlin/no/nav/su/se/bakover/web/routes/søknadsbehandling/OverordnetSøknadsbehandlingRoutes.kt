package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.web.routes.grunnlag.leggTilGrunnlagBosituasjonRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.iverksett.iverksettSøknadsbehandlingRoute
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.leggTilLovligOppholdRoute
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.leggTilUtenlandsopphold
import java.time.Clock

internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingServices: SøknadsbehandlingServices,
    revurderingService: RevurderingService,
    clock: Clock,
    satsFactory: SatsFactory,
    applicationConfig: ApplicationConfig,
) {
    søknadsbehandlingRoutes(søknadsbehandlingServices.søknadsbehandlingService, clock, satsFactory)

    leggTilUføregrunnlagRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)

    leggTilLovligOppholdRoute(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)

    leggTilGrunnlagBosituasjonRoutes(
        søknadsbehandlingServices.søknadsbehandlingService,
        revurderingService,
        satsFactory,
    )

    leggTilGrunnlagFradrag(søknadsbehandlingServices.søknadsbehandlingService, clock, satsFactory)

    leggTilUtenlandsopphold(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)

    leggTilFormueForSøknadsbehandlingRoute(søknadsbehandlingServices.søknadsbehandlingService, satsFactory, clock)

    leggTilFamiliegjenforeningRoute(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)

    pensjonsVilkårRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory, clock)

    flyktningVilkårRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory, clock)

    fastOppholdVilkårRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory, clock)

    personligOppmøteVilkårRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory, clock)

    institusjonsoppholdRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory, clock)

    iverksettSøknadsbehandlingRoute(
        søknadsbehandlingServices.iverksettSøknadsbehandlingService,
        satsFactory,
        clock,
        applicationConfig,
    )
}
