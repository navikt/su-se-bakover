package no.nav.su.se.bakover.domain.oppdrag.simulering

import økonomi.domain.simulering.SimuleringFeilet

sealed interface KontrollsimuleringFeilet {

    data class KunneIkkeSimulere(val underliggende: SimuleringFeilet) : KontrollsimuleringFeilet
    data class Forskjeller(
        val underliggende: KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet,
    ) : KontrollsimuleringFeilet
}
