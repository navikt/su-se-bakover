package no.nav.su.se.bakover.kontrollsamtale.application

import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversikt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversiktService
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleMånedOversikt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.tidslinje
import java.time.Month

class KontrollsamtaleDriftOversiktServiceImpl(
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val utbetalingsRepo: UtbetalingRepo,
    private val sakRepo: SakRepo,
) : KontrollsamtaleDriftOversiktService {

    override fun hentKontrollsamtaleOversikt(toSisteMåneder: Periode): KontrollsamtaleDriftOversikt {
        require(toSisteMåneder.getAntallMåneder() == 2) {
            "periode toSisteMåneder er lenger enn to, skal kun være inneværende og forrige måned"
        }

        val innkallinger = kontrollsamtaleService.hentKontrollsamtalerMedFristIPeriode(toSisteMåneder)

        val nyeInnkallinger = innkallinger.fristIMåned(toSisteMåneder.tilOgMed.month)
        val utgåtteKontrollsamtaler = innkallinger.fristIMåned(toSisteMåneder.fraOgMed.month)
        val sakerMedStans = sakerMedInnkaltKontrollSamtaleSomHarFørtTilStans(utgåtteKontrollsamtaler)

        return KontrollsamtaleDriftOversikt(
            inneværendeMåned = KontrollsamtaleMånedOversikt(
                antallInnkallinger = nyeInnkallinger.size,
                sakerMedStans = emptyList(),
            ),
            utgåttMåned = KontrollsamtaleMånedOversikt(
                antallInnkallinger = utgåtteKontrollsamtaler.size,
                sakerMedStans = sakerMedStans.map { it.toString() },
            ),
        )
    }

    private fun sakerMedInnkaltKontrollSamtaleSomHarFørtTilStans(utløpteKontrollSamtaler: List<Kontrollsamtale>): List<Long> {
        val saker = sakRepo.hentSakInfoBulk(utløpteKontrollSamtaler.map { it.sakId })
        return saker.filter {
            val utbetalinger = utbetalingsRepo.hentOversendteUtbetalinger(it.sakId)
            utbetalinger.tidslinje().getOrNull()?.last() is UtbetalingslinjePåTidslinje.Stans
        }.map { it.saksnummer.nummer }
    }
}

fun List<Kontrollsamtale>.fristIMåned(måned: Month): List<Kontrollsamtale> = filter { it.frist.month == måned }
