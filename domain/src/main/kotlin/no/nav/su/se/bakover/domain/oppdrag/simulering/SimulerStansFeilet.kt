package no.nav.su.se.bakover.domain.oppdrag.simulering

import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi

sealed class SimulerStansFeilet {
    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : SimulerStansFeilet()
    data class KunneIkkeGenerereUtbetaling(val feil: Utbetalingsstrategi.Stans.Feil) : SimulerStansFeilet()
}
