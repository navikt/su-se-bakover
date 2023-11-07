package no.nav.su.se.bakover.domain.revurdering.gjenopptak

import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeSimulereGjenopptakAvYtelse {
    data object FantIkkeSak : KunneIkkeSimulereGjenopptakAvYtelse
    data object FantIkkeRevurdering : KunneIkkeSimulereGjenopptakAvYtelse
    data object FantIngenVedtak : KunneIkkeSimulereGjenopptakAvYtelse
    data object FinnesÅpenGjenopptaksbehandling : KunneIkkeSimulereGjenopptakAvYtelse
    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : KunneIkkeSimulereGjenopptakAvYtelse
    data object KunneIkkeOppretteRevurdering : KunneIkkeSimulereGjenopptakAvYtelse
    data class UgyldigTypeForOppdatering(val type: KClass<out AbstraktRevurdering>) : KunneIkkeSimulereGjenopptakAvYtelse
    data object SisteVedtakErIkkeStans : KunneIkkeSimulereGjenopptakAvYtelse

    data class KunneIkkeGenerereUtbetaling(val underliggende: Utbetalingsstrategi.Gjenoppta.Feil) : KunneIkkeSimulereGjenopptakAvYtelse
}
