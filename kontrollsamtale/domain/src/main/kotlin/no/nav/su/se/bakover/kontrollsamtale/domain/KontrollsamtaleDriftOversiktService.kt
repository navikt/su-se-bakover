package no.nav.su.se.bakover.kontrollsamtale.domain

import java.time.YearMonth
import java.util.UUID

interface KontrollsamtaleDriftOversiktService {
    fun hentKontrollsamtaleOversikt(
        inneværendeMåned: YearMonth = YearMonth.now(),
    ): KontrollsamtaleDriftOversikt
}

data class KontrollsamtaleDriftOversikt(
    val utgåttMåned: KontrollsamtaleMånedOversikt,
    val inneværendeMåned: KontrollsamtaleMånedOversikt,
)

data class KontrollsamtaleMånedOversikt(
    val antallInnkallinger: Int,
    val sakerMedStans: List<UUID>,
)
