package no.nav.su.se.bakover.domain.søknadsbehandling.avslag

import vilkår.common.domain.Avslagsgrunn

interface ErAvslag {
    val avslagsgrunner: List<Avslagsgrunn>
}
