package no.nav.su.se.bakover.web.services.fradragssjekken

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.februar
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.UUID

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
    fun `kjørFradragssjekkForMåned kjører ikke tilbake i tid`() {
        val service = lagService()
        val tidligereMaaned: Måned = Måned.now(fixedClock).minusMonths(1L)

        service.kjørFradragssjekkForMånedMedValidering(
            tidligereMaaned,
            dryRun = false,
        ) shouldBeLeft FradragsSjekkFeil.DatoErTilbakeITid
    }

    @Test
    fun `kjørFradragssjekkForMåned feiler for fremtidig måned`() {
        val service = lagService()
        val nesteMaaned: Måned = Måned.now(fixedClock).plusMonths(1L)

        service.kjørFradragssjekkForMånedMedValidering(
            nesteMaaned,
            dryRun = false,
        ) shouldBeLeft FradragsSjekkFeil.DatoErFremITid
    }

    @Test
    fun `kjørFradragssjekkForMåned feiler hvis allerede kjørt for inneværende måned`() {
        val naaVærendeMåned: Måned = Måned.now(fixedClock)
        val service = lagService(
            fradragssjekkRunPostgresRepo = mock {
                on { harOrdinaerKjoringForMåned(naaVærendeMåned) } doReturn true
            },
        )

        service.kjørFradragssjekkForMånedMedValidering(
            naaVærendeMåned,
            dryRun = false,
        ) shouldBeLeft FradragsSjekkFeil.AlleredeKjørtForMåned
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

        val gjeldendeMånedsutbetaling = 5000
        val service = lagService(
            utbetalingsRepo = mock {
                on { hentOversendteUtbetalingerForSakIder(saker.map { it.sakId }) } doReturn mapOf(
                    sakMedNy.sakId to utbetalingerNy(sakId = sakMedNy.sakId, periode = måned, beløp = gjeldendeMånedsutbetaling),
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
                        beløp = gjeldendeMånedsutbetaling,
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
                gjeldendeMånedsutbetaling = gjeldendeMånedsutbetaling,
            ),
            LøpendeSakForMåned(
                sak = sakMedReaktivering,
                gjeldendeMånedsutbetaling = gjeldendeMånedsutbetaling,
            ),
        )
    }

    @Test
    fun `hentSakerMedOppgaveForrigeMåned sjekker at forrige måneds saker med oppgave  blir returnert`() {
        val måned = februar(2026)
        val saker = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val sakerMedOppgaveForrigeMåned = setOf(UUID.randomUUID(), UUID.randomUUID())
        val fradragssjekkRunPostgresRepo = mock<FradragssjekkRunPostgresRepo> {
            on {
                hentSakIderMedOppgaveOpprettetForMåned(
                    sakIder = saker,
                    måned = måned.minusMonths(1),
                )
            } doReturn sakerMedOppgaveForrigeMåned
        }
        val service = lagService(fradragssjekkRunPostgresRepo = fradragssjekkRunPostgresRepo)

        service.hentSakerMedOppgaveForrigeMåned(
            saker = saker,
            dryRun = false,
            måned = måned,
        ) shouldBe sakerMedOppgaveForrigeMåned

        verify(fradragssjekkRunPostgresRepo).hentSakIderMedOppgaveOpprettetForMåned(
            sakIder = saker,
            måned = måned.minusMonths(1),
        )
    }

    @Test
    fun `hentSakerMedOppgaveForrigeMåned returnerer ingen ignorerbare saker for dry-run og tom liste`() {
        val fradragssjekkRunPostgresRepo = mock<FradragssjekkRunPostgresRepo>()
        val service = lagService(fradragssjekkRunPostgresRepo = fradragssjekkRunPostgresRepo)
        val saker = listOf(UUID.randomUUID(), UUID.randomUUID())

        service.hentSakerMedOppgaveForrigeMåned(
            saker = saker,
            dryRun = true,
            måned = februar(2026),
        ) shouldBe emptySet()

        service.hentSakerMedOppgaveForrigeMåned(
            saker = emptyList(),
            dryRun = false,
            måned = februar(2026),
        ) shouldBe emptySet()
        verifyNoInteractions(fradragssjekkRunPostgresRepo)
    }

    private fun lagService(
        fradragssjekkRunPostgresRepo: FradragssjekkRunPostgresRepo = mock(),
        utbetalingsRepo: økonomi.domain.utbetaling.UtbetalingRepo = defaultMock(),
        fradragssjekkOppgaveoppretter: FradragssjekkOppgaveoppretter = defaultMock(),
    ): FradragsjobbenServiceImpl {
        return FradragsjobbenServiceImpl(
            aapKlient = defaultMock(),
            pesysKlient = defaultMock(),
            sakService = defaultMock(),
            fradragssjekkOppgaveoppretter = fradragssjekkOppgaveoppretter,
            utbetalingsRepo = utbetalingsRepo,
            satsFactory = defaultMock(),
            fradragssjekkRunPostgresRepo = fradragssjekkRunPostgresRepo,
            clock = fixedClock,
        )
    }
}
