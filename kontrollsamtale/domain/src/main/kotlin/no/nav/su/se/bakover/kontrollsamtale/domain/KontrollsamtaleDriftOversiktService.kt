package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.tid.periode.Periode
import java.time.YearMonth
import java.util.UUID

interface KontrollsamtaleDriftOversiktService {
    fun hentKontrollsamtaleOversikt(
        toSisteMåneder: Periode = Periode.create(
            fraOgMed = YearMonth.now().minusMonths(1).atDay(1),
            tilOgMed = YearMonth.now().atEndOfMonth(),
        ),
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
