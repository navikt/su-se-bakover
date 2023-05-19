package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.sak.SakInfo

/**
 * SU-brukere som har innvilget stønad for denne måneden eller senere
 * De som har fått opphør er ikke inkludert.
 */
data class InnvilgetForMånedEllerSenere(
    val fraOgMedEllerSenere: Måned,
    val sakInfo: List<SakInfo>,
)
