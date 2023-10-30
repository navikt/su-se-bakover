package no.nav.su.se.bakover.domain.revurdering.stans

import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeIverksetteStansYtelse {
    data object KunneIkkeUtbetale : KunneIkkeIverksetteStansYtelse
    data object FantIkkeRevurdering : KunneIkkeIverksetteStansYtelse
    data class UgyldigTilstand(
        val faktiskTilstand: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteStansYtelse {
        val målTilstand: KClass<out StansAvYtelseRevurdering.IverksattStansAvYtelse> =
            StansAvYtelseRevurdering.IverksattStansAvYtelse::class
    }
    data object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteStansYtelse
    data class UkjentFeil(val msg: String) : KunneIkkeIverksetteStansYtelse
    data object DetHarKommetNyeOverlappendeVedtak : KunneIkkeIverksetteStansYtelse
    data class KontrollsimuleringFeilet(
        val underliggende: no.nav.su.se.bakover.domain.oppdrag.simulering.KontrollsimuleringFeilet,
    ) : KunneIkkeIverksetteStansYtelse

    data class KunneIkkeGenerereUtbetaling(
        val underliggende: Utbetalingsstrategi.Stans.Feil,
    ) : KunneIkkeIverksetteStansYtelse
}
