package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn

interface Avslag {
    val avslagsgrunner: List<Avslagsgrunn>
}
