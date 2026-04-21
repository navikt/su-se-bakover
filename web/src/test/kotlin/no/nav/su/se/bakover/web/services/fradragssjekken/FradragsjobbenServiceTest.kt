package no.nav.su.se.bakover.web.services.fradragssjekken

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.sakInfo
import no.nav.su.se.bakover.test.utbetaling.utbetalingerNy
import no.nav.su.se.bakover.test.utbetaling.utbetalingerOpphør
import no.nav.su.se.bakover.test.utbetaling.utbetalingerReaktivering
import no.nav.su.se.bakover.test.utbetaling.utbetalingerStans
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.util.UUID
import kotlin.test.assertFailsWith

internal class FradragsjobbenServiceTest {

    @Test
    fun `kan ikke kjøre fradragssjekk for fortiden`() {
        val service = lagService()
        val tidligereMaaned: Måned = Måned.now(fixedClock).minusMonths(1L)

        service.validerKjøringForMåned(tidligereMaaned) shouldBe FradragsSjekkFeil.DatoErTilbakeITid
    }

    @Test
    fun `kan ikke kjøre fradragssjekk for fremtiden`() {
        val service = lagService()
        val nesteMaaned: Måned = Måned.now(fixedClock).plusMonths(1L)

        service.validerKjøringForMåned(nesteMaaned) shouldBe FradragsSjekkFeil.DatoErFremITid
    }

    @Test
    fun `kan kjøre fradragssjekk for inneværende måned`() {
        val naaVærendeMåned: Måned = Måned.now(fixedClock)

        val service = lagService(
            fradragssjekkRunPostgresRepo = mock {
                on { harOrdinaerKjoringForMåned(naaVærendeMåned) } doReturn false
            },
        )

        service.validerKjøringForMåned(naaVærendeMåned) shouldBe null
    }

    @Test
    fun `kan ikke kjøre fradragssjekk for inneværende måned hvis vanlig kjøring er gjort`() {
        val naaVærendeMåned: Måned = Måned.now(fixedClock)

        val service = lagService(
            fradragssjekkRunPostgresRepo = mock {
                on { harOrdinaerKjoringForMåned(naaVærendeMåned) } doReturn true
            },
        )
        service.validerKjøringForMåned(naaVærendeMåned) shouldBe FradragsSjekkFeil.AlleredeKjørtForMåned
    }

    @Test
    fun `direkte kall til kjørFradragssjekkForMåned validerer også måned`() {
        val service = lagService()
        val tidligereMaaned: Måned = Måned.now(fixedClock).minusMonths(1L)

        assertFailsWith<IllegalArgumentException> {
            service.kjørFradragssjekkForMånedMedValidering(tidligereMaaned, dryRun = false)
        }
    }

    @Test
    fun `hentSakerMedLøpendeUtbetalingForMåned returnerer tom liste når saker er tom`() {
        val utbetalingsRepo = defaultMock<økonomi.domain.utbetaling.UtbetalingRepo>()
        val service = lagService(utbetalingsRepo = utbetalingsRepo)

        service.hentSakerMedLøpendeUtbetalingForMåned(
            saker = emptyList(),
            måned = Måned.now(fixedClock),
        ) shouldBe emptyList()

        verifyNoInteractions(utbetalingsRepo)
    }

    @Test
    fun `hentSakerMedLøpendeUtbetalingForMåned inkluderer kun saker med ny eller reaktivering`() {
        val måned = Måned.now(fixedClock)
        val sakMedNy = sakInfo(sakId = UUID.randomUUID())
        val sakMedReaktivering = sakInfo(sakId = UUID.randomUUID())
        val sakMedOpphør = sakInfo(sakId = UUID.randomUUID())
        val sakMedStans = sakInfo(sakId = UUID.randomUUID())
        val sakUtenGjeldendeUtbetaling = sakInfo(sakId = UUID.randomUUID())
        val saker = listOf(sakMedNy, sakMedReaktivering, sakMedOpphør, sakMedStans, sakUtenGjeldendeUtbetaling)

        val service = lagService(
            utbetalingsRepo = mock {
                on { hentOversendteUtbetalingerForSakIder(saker.map { it.sakId }) } doReturn mapOf(
                    sakMedNy.sakId to utbetalingerNy(sakId = sakMedNy.sakId, periode = måned),
                    sakMedReaktivering.sakId to utbetalingerReaktivering(
                        sakId = sakMedReaktivering.sakId,
                        nyPeriode = måned,
                        stansFraOgMed = måned.fraOgMed,
                        reaktiveringFraOgMed = måned.fraOgMed,
                    ),
                    sakMedOpphør.sakId to utbetalingerOpphør(
                        sakId = sakMedOpphør.sakId,
                        nyPeriode = måned,
                        opphørsperiode = måned,
                    ),
                    sakMedStans.sakId to utbetalingerStans(
                        sakId = sakMedStans.sakId,
                        nyPeriode = måned,
                        stansFraOgMed = måned.fraOgMed,
                    ),
                    sakUtenGjeldendeUtbetaling.sakId to utbetalingerNy(
                        sakId = sakUtenGjeldendeUtbetaling.sakId,
                        periode = måned.minusMonths(1L),
                    ),
                )
            },
        )

        service.hentSakerMedLøpendeUtbetalingForMåned(
            saker = saker,
            måned = måned,
        ) shouldContainExactly listOf(
            LøpendeSakForMåned(
                sak = sakMedNy,
                gjeldendeMånedsutbetaling = 5000,
            ),
            LøpendeSakForMåned(
                sak = sakMedReaktivering,
                gjeldendeMånedsutbetaling = 5000,
            ),
        )
    }

    private fun lagService(
        fradragssjekkRunPostgresRepo: FradragssjekkRunPostgresRepo = mock(),
        utbetalingsRepo: økonomi.domain.utbetaling.UtbetalingRepo = defaultMock(),
    ): FradragsjobbenServiceImpl {
        return FradragsjobbenServiceImpl(
            aapKlient = defaultMock(),
            pesysKlient = defaultMock(),
            sakService = defaultMock(),
            oppgaveService = defaultMock(),
            utbetalingsRepo = utbetalingsRepo,
            satsFactory = defaultMock(),
            fradragssjekkRunPostgresRepo = fradragssjekkRunPostgresRepo,
            clock = fixedClock,
        )
    }
}
