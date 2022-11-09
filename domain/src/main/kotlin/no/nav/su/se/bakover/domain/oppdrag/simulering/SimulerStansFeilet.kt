package no.nav.su.se.bakover.domain.oppdrag.simulering

import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet

sealed class SimulerStansFeilet {
    data class KunneIkkeSimulere(val feil: SimulerUtbetalingFeilet) : SimulerStansFeilet()
    data class KunneIkkeGenerereUtbetaling(val feil: Utbetalingsstrategi.Stans.Feil) : SimulerStansFeilet()
}
