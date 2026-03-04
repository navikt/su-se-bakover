package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.kontrollsamtale.application.KontrollsamtaleDriftOversiktServiceImpl
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.kontrollsamtale.innkaltKontrollsamtale
import no.nav.su.se.bakover.test.sakInfo
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeStans
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.Utbetalinger
import java.time.YearMonth
import java.util.UUID

class KontrollsamtaleDriftOversiktServiceImplTest {

    private val kontrollsamtaleService = mock<KontrollsamtaleService> {
        on {
            hentKontrollsamtalerMedFristIPeriode(toSisteMåneder)
        } doReturn listOf(
            sak1.kontrollsamtale,
            sak2.kontrollsamtale,
            sak3.kontrollsamtale,
            sak4.kontrollsamtale,
            sak5.kontrollsamtale,
        )
    }
    private val sakRepo = mock<SakRepo> {
        on {
            hentSakInfoBulk(listOf(sak3.sakInfo.sakId, sak4.sakInfo.sakId, sak5.sakInfo.sakId))
        } doReturn listOf(sak3.sakInfo, sak4.sakInfo, sak5.sakInfo)
    }
    private val utbetalingsRepo = mock<UtbetalingRepo> {
        on { hentOversendteUtbetalinger(sak1.sakInfo.sakId) } doReturn sak1.utbetalinger
        on { hentOversendteUtbetalinger(sak2.sakInfo.sakId) } doReturn sak2.utbetalinger
        on { hentOversendteUtbetalinger(sak3.sakInfo.sakId) } doReturn sak3.utbetalinger
        on { hentOversendteUtbetalinger(sak4.sakInfo.sakId) } doReturn sak4.utbetalinger
        on { hentOversendteUtbetalinger(sak5.sakInfo.sakId) } doReturn sak5.utbetalinger
    }
    private val service = KontrollsamtaleDriftOversiktServiceImpl(kontrollsamtaleService, utbetalingsRepo, sakRepo)

    @Test
    fun `henter oversikt over kontrollsamtaler inneværende og forrige måned`() {
        val result = service.hentKontrollsamtaleOversikt(toSisteMåneder)
        with(result.inneværendeMåned) {
            antallInnkallinger shouldBe 2
            sakerMedStans.size shouldBe 0
        }
        with(result.utgåttMåned) {
            // i praksis skal det ikke være diff på disse da kontrollsamtaler på dette tidspunktet
            // enten skal ha status gjennomført eller ha blitt stanset.
            // Men velger å la en gjenstå med status innkalt for å teste filtrering på stans
            antallInnkallinger shouldBe 3
            sakerMedStans.size shouldBe 2
            sakerMedStans.shouldContainAll(listOf(sak4.sakInfo.saksnummer.nummer, sak5.sakInfo.saksnummer.nummer).map { it.toString() })
        }
    }

    companion object {
        private val januar = YearMonth.of(2026, 1)
        private val februar = YearMonth.of(2026, 2)
        private val toSisteMåneder = Periode.create(januar.atDay(1), februar.atEndOfMonth())

        val sak1 = testSak(3001, februar)
        val sak2 = testSak(3002, februar)
        val sak3 = testSak(3003, januar)
        val sak4 = testSak(3004, januar, stanset = true)
        val sak5 = testSak(3005, januar, stanset = true)

        private fun testSak(
            saksnummer: Int,
            kontrollsamtaleMåned: YearMonth,
            stanset: Boolean = false,
        ): TestSakMedKontrollsamtaleOgUtbetaling {
            val sakId = UUID.randomUUID()
            return TestSakMedKontrollsamtaleOgUtbetaling(
                sakInfo = sakInfo(sakId = sakId, saksnummer = Saksnummer.parse(saksnummer.toString())),
                kontrollsamtale = innkaltKontrollsamtale(
                    sakId = sakId,
                    innkallingsdato = kontrollsamtaleMåned.atDay(1),
                    frist = kontrollsamtaleMåned.atEndOfMonth(),
                ),
                utbetalinger = if (stanset) {
                    val første = utbetalingslinjeNy()
                    val stans = utbetalingslinjeStans(utbetalingslinjeSomSkalEndres = første, clock = TikkendeKlokke())
                    Utbetalinger(
                        oversendtUtbetalingUtenKvittering(utbetalingslinjer = nonEmptyListOf(første, stans)),
                    )
                } else {
                    Utbetalinger(
                        oversendtUtbetalingMedKvittering(utbetalingslinjer = nonEmptyListOf(utbetalingslinjeNy())),
                    )
                },
            )
        }
    }
}

data class TestSakMedKontrollsamtaleOgUtbetaling(
    val sakInfo: SakInfo,
    val kontrollsamtale: Kontrollsamtale,
    val utbetalinger: Utbetalinger,
)
