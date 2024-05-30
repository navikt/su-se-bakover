package no.nav.su.se.bakover.kontrollsamtale.domain.opprett

import no.nav.su.se.bakover.common.domain.tid.periode.IkkeOverlappendePerioder
import no.nav.su.se.bakover.common.tid.periode.Måned

sealed interface KanIkkeOppretteKontrollsamtale {
    data class UgyldigInnkallingsmåned(val innkallingsmåned: Måned) : KanIkkeOppretteKontrollsamtale
    data class InnkallingsmånedMåVæreEtterNåværendeMåned(val innkallingsmåned: Måned) : KanIkkeOppretteKontrollsamtale

    data class InnkallingsmånedUtenforStønadsperiode(
        val innkallingsmåned: Måned,
        val stønadsperioder: IkkeOverlappendePerioder,
    ) : KanIkkeOppretteKontrollsamtale

    data object IngenStønadsperiode : KanIkkeOppretteKontrollsamtale
}
