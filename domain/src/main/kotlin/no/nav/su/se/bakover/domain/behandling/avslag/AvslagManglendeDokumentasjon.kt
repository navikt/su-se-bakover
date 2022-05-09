package no.nav.su.se.bakover.domain.behandling.avslag

import no.nav.su.se.bakover.domain.søknadsbehandling.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

data class AvslagManglendeDokumentasjon(
    val søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
) : ErAvslag {
    override val avslagsgrunner: List<Avslagsgrunn> = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)
}
