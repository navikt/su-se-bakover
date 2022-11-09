package no.nav.su.se.bakover.domain.oppdrag.simulering

import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet

sealed class SimulerGjenopptakFeil {
    data class KunneIkkeSimulere(val feil: SimulerUtbetalingFeilet) : SimulerGjenopptakFeil()
    data class KunneIkkeGenerereUtbetaling(val feil: Utbetalingsstrategi.Gjenoppta.Feil) : SimulerGjenopptakFeil()
}
