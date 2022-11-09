package no.nav.su.se.bakover.domain.revurdering.gjenopptak

import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import kotlin.reflect.KClass

sealed class KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering {
    data class KunneIkkeUtbetale(val feil: UtbetalGjenopptakFeil) : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering()
    object FantIkkeRevurdering : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering()
    data class UgyldigTilstand(
        val faktiskTilstand: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering() {
        val målTilstand: KClass<out GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> =
            GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse::class
    }

    object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering()
    object LagringFeilet : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering()
}
