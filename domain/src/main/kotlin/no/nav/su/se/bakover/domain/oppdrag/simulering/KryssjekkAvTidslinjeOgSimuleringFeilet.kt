package no.nav.su.se.bakover.domain.oppdrag.simulering

import økonomi.domain.simulering.ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode
import økonomi.domain.simulering.SimuleringFeilet

sealed interface KryssjekkAvTidslinjeOgSimuleringFeilet {
    data class KryssjekkFeilet(val feil: ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode) : KryssjekkAvTidslinjeOgSimuleringFeilet

    data object RekonstruertUtbetalingsperiodeErUlikOpprinnelig : KryssjekkAvTidslinjeOgSimuleringFeilet

    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : KryssjekkAvTidslinjeOgSimuleringFeilet

    data object KunneIkkeGenerereTidslinje : KryssjekkAvTidslinjeOgSimuleringFeilet
}
