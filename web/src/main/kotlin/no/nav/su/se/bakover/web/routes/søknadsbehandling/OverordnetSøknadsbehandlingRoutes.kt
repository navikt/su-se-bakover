package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.leggTilLovligOppholdRoute
import java.time.Clock

internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    clock: Clock,
    satsFactory: SatsFactory,
) {
    søknadsbehandlingRoutes(søknadsbehandlingService, clock, satsFactory)

    leggTilUføregrunnlagRoutes(søknadsbehandlingService, satsFactory)

    leggTilLovligOppholdRoute(søknadsbehandlingService, satsFactory)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingService, satsFactory)

    leggTilGrunnlagFradrag(søknadsbehandlingService, clock, satsFactory)

    leggTilUtenlandsopphold(søknadsbehandlingService, satsFactory)

    leggTilFormueForSøknadsbehandlingRoute(søknadsbehandlingService, satsFactory)

    leggTilFamiliegjenforeningRoute(søknadsbehandlingService, satsFactory)

    pensjonsVilkårRoutes(søknadsbehandlingService, satsFactory)

    flyktningVilkårRoutes(søknadsbehandlingService, satsFactory)

    fastOppholdVilkårRoutes(søknadsbehandlingService, satsFactory)

    personligOppmøteVilkårRoutes(søknadsbehandlingService, satsFactory)

    institusjonsoppholdRoutes(søknadsbehandlingService, satsFactory, clock)
}
