package behandling.søknadsbehandling.domain.avslag

import vilkår.common.domain.Avslagsgrunn

interface ErAvslag {
    val avslagsgrunner: List<Avslagsgrunn>
}
