package no.nav.su.se.bakover.domain.revurdering.stans

import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeIverksetteStansYtelse {
    data class KunneIkkeUtbetale(val feil: UtbetalStansFeil) : KunneIkkeIverksetteStansYtelse
    object FantIkkeRevurdering : KunneIkkeIverksetteStansYtelse
    data class UgyldigTilstand(
        val faktiskTilstand: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteStansYtelse {
        val målTilstand: KClass<out StansAvYtelseRevurdering.IverksattStansAvYtelse> =
            StansAvYtelseRevurdering.IverksattStansAvYtelse::class
    }

    object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteStansYtelse
    data class UkjentFeil(val msg: String) : KunneIkkeIverksetteStansYtelse
    object DetHarKommetNyeOverlappendeVedtak : KunneIkkeIverksetteStansYtelse
}
