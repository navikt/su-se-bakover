package no.nav.su.se.bakover.kontrollsamtale.application

import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversikt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversiktService
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleMånedOversikt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.tidslinje
import java.time.YearMonth
import java.util.UUID

class KontrollsamtaleDriftOversiktServiceImpl(
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val utbetalingsRepo: UtbetalingRepo,
) : KontrollsamtaleDriftOversiktService {

    override fun hentKontrollsamtaleOversikt(inneværendeMåned: YearMonth): KontrollsamtaleDriftOversikt {
        val innkalliger = kontrollsamtaleService.hentInnkalteKontrollsamtalerMedFristUtløptPåDato(inneværendeMåned.atEndOfMonth().plusDays(1))

        val utgåtteKontrollsamtaler = innkalliger.utgårIMåned(inneværendeMåned.minusMonths(1))
        val sakerMedStans = sakerMedInnkaltKontrollSamtaleSomHarFørtTilStans(utgåtteKontrollsamtaler)

        return KontrollsamtaleDriftOversikt(
            inneværendeMåned = KontrollsamtaleMånedOversikt(
                antallInnkallinger = innkalliger.utgårIMåned(inneværendeMåned).size,
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

fun List<Kontrollsamtale>.utgårIMåned(måned: YearMonth) = filter { it.frist.month == måned.month }
