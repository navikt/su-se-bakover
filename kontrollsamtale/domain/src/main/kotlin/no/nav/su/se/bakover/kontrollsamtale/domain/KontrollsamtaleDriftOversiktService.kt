package no.nav.su.se.bakover.kontrollsamtale.domain

import java.time.LocalDate
import java.util.UUID

interface KontrollsamtaleDriftOversiktService {
    fun hentKontrollsamtaleOversikt(): KontrollsamtaleDriftOversikt
}

data class KontrollsamtaleDriftOversikt(
    val inneværendeMåned: KontrollsamtaleMånedOversikt,
    val nesteMåned: KontrollsamtaleMånedOversikt,
)

data class KontrollsamtaleMånedOversikt(
    val frist: LocalDate,
    val antallInnkallinger: Int,
    val sakerMedStans: List<UUID>,
)
