package no.nav.su.se.bakover.domain.oppdrag.simulering

sealed interface KontrollsimuleringFeilet {

    data class KunneIkkeSimulere(val underliggende: SimuleringFeilet) : KontrollsimuleringFeilet
    data class Forskjeller(
        val underliggende: KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet,
    ) : KontrollsimuleringFeilet
}
