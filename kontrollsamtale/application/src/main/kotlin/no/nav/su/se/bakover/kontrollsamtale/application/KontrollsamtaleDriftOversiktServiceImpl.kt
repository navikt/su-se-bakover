package no.nav.su.se.bakover.kontrollsamtale.application

import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversikt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversiktService
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleMånedOversikt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.tidslinje
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class KontrollsamtaleDriftOversiktServiceImpl(
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val utbetalingsRepo: UtbetalingRepo,
) : KontrollsamtaleDriftOversiktService {

    override fun hentKontrollsamtaleOversikt(): KontrollsamtaleDriftOversikt {
        val nå = YearMonth.now().atEndOfMonth()
        val nesteMåned = nå.plusMonths(1)
        val innkalliger = kontrollsamtaleService.hentInnkalteKontrollsamtalerMedFristUtløptPåDato(nesteMåned)

        val innkallingerSomUtløperDenneMåneden = innkalliger.antallPerFrist(nå)
        val sakerMedStansDenneMåneden = sakerMedInnkaltKontrollSamtaleSomHarFørtTilStans(innkallingerSomUtløperDenneMåneden)

        return KontrollsamtaleDriftOversikt(
            nesteMåned = KontrollsamtaleMånedOversikt(
                frist = nesteMåned,
                antallInnkallinger = innkalliger.antallPerFrist(nesteMåned).size,
                sakerMedStans = emptyList(),
            ),
            inneværendeMåned = KontrollsamtaleMånedOversikt(
                frist = nå,
                antallInnkallinger = innkallingerSomUtløperDenneMåneden.size,
                sakerMedStans = sakerMedStansDenneMåneden,
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

fun List<Kontrollsamtale>.antallPerFrist(frist: LocalDate) = filter { it.frist == frist }
