package no.nav.su.se.bakover.domain.revurdering.gjenopptak

import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering {
    /**
     * Gjelder kun i det tilfellet vi ikke klarte overføre utbetalingen til oppdragskøen.
     */
    data object KunneIkkeUtbetale : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering
    data object FantIkkeRevurdering : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering

    data class UgyldigTilstand(
        val faktiskTilstand: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering {
        val målTilstand: KClass<out GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> =
            GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse::class
    }

    data object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering

    data object LagringFeilet : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering

    data object DetHarKommetNyeOverlappendeVedtak : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering

    data class KontrollsimuleringFeilet(val underliggende: no.nav.su.se.bakover.domain.oppdrag.simulering.KontrollsimuleringFeilet) : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering

    data class KunneIkkeGenerereUtbetaling(val underliggende: Utbetalingsstrategi.Gjenoppta.Feil) : KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering
}
