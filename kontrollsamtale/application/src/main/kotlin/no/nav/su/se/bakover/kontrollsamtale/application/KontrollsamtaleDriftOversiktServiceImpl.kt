package no.nav.su.se.bakover.kontrollsamtale.application

import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversikt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversiktService
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleMånedOversikt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.tidslinje
import java.time.Month
import java.util.UUID

class KontrollsamtaleDriftOversiktServiceImpl(
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val utbetalingsRepo: UtbetalingRepo,
) : KontrollsamtaleDriftOversiktService {

    override fun hentKontrollsamtaleOversikt(toSisteMåneder: Periode): KontrollsamtaleDriftOversikt {
        val innkalliger = kontrollsamtaleService.hentKontrollsamtalerMedFristIPeriode(toSisteMåneder)

        val nyeInnkallinger = innkalliger.fristIMåned(toSisteMåneder.tilOgMed.month)
        val utgåtteKontrollsamtaler = innkalliger.fristIMåned(toSisteMåneder.fraOgMed.month)
        val sakerMedStans = sakerMedInnkaltKontrollSamtaleSomHarFørtTilStans(utgåtteKontrollsamtaler)

        return KontrollsamtaleDriftOversikt(
            inneværendeMåned = KontrollsamtaleMånedOversikt(
                antallInnkallinger = nyeInnkallinger.size,
                sakerMedStans = emptyList(),
            ),
            utgåttMåned = KontrollsamtaleMånedOversikt(
                antallInnkallinger = utgåtteKontrollsamtaler.size,
                sakerMedStans = sakerMedStans,
            ),
        )
    }

    private fun sakerMedInnkaltKontrollSamtaleSomHarFørtTilStans(utløpteKontrollSamtaler: List<Kontrollsamtale>): List<UUID> {
        return utløpteKontrollSamtaler.filter {
            val utbetalinger = utbetalingsRepo.hentOversendteUtbetalinger(it.sakId)
            utbetalinger.tidslinje().getOrNull()?.last() is UtbetalingslinjePåTidslinje.Stans
        }.map { it.sakId }
    }
}

fun List<Kontrollsamtale>.fristIMåned(måned: Month): List<Kontrollsamtale> = filter { it.frist.month == måned }
