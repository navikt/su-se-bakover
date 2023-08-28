package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.web.routes.søknadsbehandling.iverksett.iverksettSøknadsbehandlingRoute
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.fastOppholdVilkårRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.flyktningVilkårRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.hentSamletSkattegrunnlagRoute
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.institusjonsoppholdRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilFamiliegjenforeningRoute
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilFormueForSøknadsbehandlingRoute
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilGrunnlagBosituasjonRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilGrunnlagFradrag
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilUføregrunnlagRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.pensjonsVilkårRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.personligOppmøteVilkårRoutes
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.leggTilLovligOppholdRoute
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.leggTilUtenlandsopphold
import java.time.Clock

internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingServices: SøknadsbehandlingServices,
    clock: Clock,
    satsFactory: SatsFactory,
    applicationConfig: ApplicationConfig,
) {
    søknadsbehandlingRoutes(søknadsbehandlingServices.søknadsbehandlingService, clock, satsFactory)

    leggTilUføregrunnlagRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)

    leggTilLovligOppholdRoute(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)

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
    hentSamletSkattegrunnlagRoute(søknadsbehandlingServices.søknadsbehandlingService, satsFactory)
}
