package no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned

import no.nav.su.se.bakover.common.domain.tid.periode.IkkeOverlappendePerioder
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.util.UUID

sealed interface KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale {
    data class KontrollsamtaleAnnullert(
        val kontrollsamtaleId: UUID,
    ) : KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale

    data class KanKunOppdatereInnkallingsmånedForPlanlagtInnkalling(
        val kontrollsamtaleId: UUID,
    ) : KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale

    data class InnkallingsmånedUtenforStønadsperiode(
        val innkallingsmåned: Måned,
        val stønadsperioder: IkkeOverlappendePerioder,
    ) : KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale

    data class UgyldigInnkallingsmåned(
        val innkallingsmåned: Måned,
    ) : KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale

    data class InnkallingsmånedMåVæreEtterNåværendeMåned(
        val innkallingsmåned: Måned,
    ) : KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale
}
