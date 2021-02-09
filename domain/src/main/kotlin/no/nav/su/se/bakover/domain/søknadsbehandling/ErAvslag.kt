package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn

interface ErAvslag {
    val avslagsgrunner: List<Avslagsgrunn>
}
