package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned

/**
 * SU-brukere som har innvilget stønad for denne måned.
 * De som har fått opphør er ikke inkludert.
 */
data class InnvilgetForMåned(
    val måned: Måned,
    val fnr: List<Fnr>,
)
