package no.nav.su.se.bakover.domain.søknadsbehandling.simuler

import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import kotlin.reflect.KClass

sealed interface KunneIkkeSimulereBehandling {
    data class KunneIkkeSimulere(val feil: SimulerUtbetalingFeilet) : KunneIkkeSimulereBehandling
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out SimulertSøknadsbehandling> = SimulertSøknadsbehandling::class,
    ) : KunneIkkeSimulereBehandling
}
