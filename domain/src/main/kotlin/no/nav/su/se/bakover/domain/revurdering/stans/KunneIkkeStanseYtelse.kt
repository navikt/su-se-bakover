package no.nav.su.se.bakover.domain.revurdering.stans

import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerStansFeilet
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import kotlin.reflect.KClass

sealed class KunneIkkeStanseYtelse {
    object FantIkkeRevurdering : KunneIkkeStanseYtelse()
    object FantIkkeSak : KunneIkkeStanseYtelse()
    object SakHarÅpenBehandling : KunneIkkeStanseYtelse()
    data class SimuleringAvStansFeilet(val feil: SimulerStansFeilet) : KunneIkkeStanseYtelse()
    object KunneIkkeOppretteRevurdering : KunneIkkeStanseYtelse()
    data class UgyldigTypeForOppdatering(val type: KClass<out AbstraktRevurdering>) : KunneIkkeStanseYtelse()

    data class UkjentFeil(val msg: String) : KunneIkkeStanseYtelse()
}
