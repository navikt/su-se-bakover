package behandling.regulering.domain.simulering

import økonomi.domain.simulering.ForskjellerMellomUtbetalingOgSimulering

sealed interface KunneIkkeSimulereRegulering {
    data object FantIngenBeregning : KunneIkkeSimulereRegulering
    data object ManglerUføreGrunnlag : KunneIkkeSimulereRegulering

    /**
     * Underliggende [økonomi.domain.simulering.SimuleringFeilet] beholdes slik at
     * årsaken (f.eks. [økonomi.domain.simulering.SimuleringFeilet.UtenforÅpningstid])
     * propageres ut til loggene og reguleringsresultatet i stedet for å gå tapt.
     */
    data class SimuleringFeilet(
        val underliggende: økonomi.domain.simulering.SimuleringFeilet,
    ) : KunneIkkeSimulereRegulering

    data class Forskjeller(val underliggende: ForskjellerMellomUtbetalingOgSimulering) : KunneIkkeSimulereRegulering
}
