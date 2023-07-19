package no.nav.su.se.bakover.domain.revurdering.stans

import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerStansFeilet
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeStanseYtelse {

    data object FantIkkeSak : KunneIkkeStanseYtelse
    data object FantIkkeRevurdering : KunneIkkeStanseYtelse

    data object FinnesÅpenStansbehandling : KunneIkkeStanseYtelse
    data class SimuleringAvStansFeilet(val feil: SimulerStansFeilet) : KunneIkkeStanseYtelse

    data object SimuleringInneholderFeilutbetaling : KunneIkkeStanseYtelse
    data object KunneIkkeOppretteRevurdering : KunneIkkeStanseYtelse
    data class UgyldigTypeForOppdatering(val type: KClass<out AbstraktRevurdering>) : KunneIkkeStanseYtelse
    data class UkjentFeil(val msg: String) : KunneIkkeStanseYtelse
}
