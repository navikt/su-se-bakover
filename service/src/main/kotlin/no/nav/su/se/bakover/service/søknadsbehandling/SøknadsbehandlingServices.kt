package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService

data class SøknadsbehandlingServices(
    val iverksettSøknadsbehandlingService: IverksettSøknadsbehandlingService,
    val søknadsbehandlingService: SøknadsbehandlingService,
)
