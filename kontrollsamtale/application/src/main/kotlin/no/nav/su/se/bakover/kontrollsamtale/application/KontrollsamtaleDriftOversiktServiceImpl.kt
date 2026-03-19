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

    /**
     * Denne kan bli minne heavy og om den noengang skulle feile på det så må man lage egen domenemodell for utbetaling uten simulering og de andre tunge
     * objektene som er unødvendig for å lage en tidslinje for en utbetaling
     */
    private fun sakerMedInnkaltKontrollSamtaleSomHarFørtTilStans(utløpteKontrollSamtaler: List<Kontrollsamtale>): List<Long> {
        val sakIder = utløpteKontrollSamtaler.map { it.sakId }.distinct()
        if (sakIder.isEmpty()) return emptyList()

        val utbetalingerPerSak = utbetalingsRepo.hentOversendteUtbetalingerForSakIder(sakIder)
        val stansedeSakIder = utbetalingerPerSak.filterValues {
            it.tidslinje().getOrNull()?.last() is UtbetalingslinjePåTidslinje.Stans
        }.keys

        if (stansedeSakIder.isEmpty()) return emptyList()

        return sakRepo.hentSakInfoBulk(stansedeSakIder.toList()).map { it.saksnummer.nummer }
    }
}

fun List<Kontrollsamtale>.fristIMåned(måned: Month): List<Kontrollsamtale> = filter { it.frist.month == måned }
