package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.web.routes.søknadsbehandling.iverksett.iverksettSøknadsbehandlingRoute
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.fastOppholdVilkårRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.flyktningVilkårRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.institusjonsoppholdRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilFamiliegjenforeningRoute
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilFormueForSøknadsbehandlingRoute
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilGrunnlagBosituasjonRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilGrunnlagFradrag
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.leggTilUføregrunnlagRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.oppdaterSkattegrunnlagRoute
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.pensjonsVilkårRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.personligOppmøteVilkårRoutes
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.leggTilLovligOppholdRoute
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.leggTilUtenlandsopphold
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingServices: SøknadsbehandlingServices,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
    applicationConfig: ApplicationConfig,
) {
    søknadsbehandlingRoutes(søknadsbehandlingServices.søknadsbehandlingService, clock, formuegrenserFactory)

    leggTilUføregrunnlagRoutes(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory)

    leggTilLovligOppholdRoute(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory)

    leggTilGrunnlagFradrag(søknadsbehandlingServices.søknadsbehandlingService, clock, formuegrenserFactory)

    leggTilUtenlandsopphold(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory)

    leggTilFormueForSøknadsbehandlingRoute(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory, clock)

    leggTilFamiliegjenforeningRoute(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory)

    pensjonsVilkårRoutes(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory, clock)

    flyktningVilkårRoutes(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory, clock)

    fastOppholdVilkårRoutes(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory, clock)

    personligOppmøteVilkårRoutes(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory, clock)

    institusjonsoppholdRoutes(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory, clock)

    iverksettSøknadsbehandlingRoute(
        søknadsbehandlingServices.iverksettSøknadsbehandlingService,
        formuegrenserFactory,
        clock,
        applicationConfig,
    )
    oppdaterSkattegrunnlagRoute(søknadsbehandlingServices.søknadsbehandlingService, formuegrenserFactory)
}
