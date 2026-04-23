package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.right
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.oppgave.oppgaveId
import no.nav.su.se.bakover.test.sakInfo
import no.nav.su.se.bakover.test.utbetaling.utbetalingerNy
import no.nav.su.se.bakover.test.utbetaling.utbetalingerOpphør
import no.nav.su.se.bakover.test.utbetaling.utbetalingerReaktivering
import no.nav.su.se.bakover.test.utbetaling.utbetalingerStans
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
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
    fun `bruker v1 klient for fradragssjekk utenfor dev`() {
        val sak = sakInfo()
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }
        val oppgaveV2Client = mock<OppgaveV2Client>()
        val service = lagService(
            oppgaveService = oppgaveService,
            oppgaveV2Client = oppgaveV2Client,
            brukOppgaveV2 = false,
        )

        service.opprettOppgaveForFradrag(
            sak = sak,
            måned = Måned.now(fixedClock),
            avvik = listOf(
                Fradragsfunn.Oppgavegrunnlag(
                    kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10_PROSENT,
                    oppgavetekst = "Avvik",
                ),
            ),
        ) shouldBe OppgaveopprettelseResultat.Opprettet(
            oppgaveId = oppgaveId,
            sakId = sak.sakId,
        )

        verify(oppgaveService).opprettOppgaveMedSystembruker(any())
        verifyNoInteractions(oppgaveV2Client)
    }

    @Test
    fun `bruker v2 klient for fradragssjekk i dev`() {
        val sak = sakInfo()
        val oppgaveService = mock<OppgaveService>()
        val oppgaveV2Client = mock<OppgaveV2Client> {
            on { opprettOppgaveMedSystembruker(any(), any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
        }
        val service = lagService(
            oppgaveService = oppgaveService,
            oppgaveV2Client = oppgaveV2Client,
            brukOppgaveV2 = true,
        )

        service.opprettOppgaveForFradrag(
            sak = sak,
            måned = Måned.now(fixedClock),
            avvik = listOf(
                Fradragsfunn.Oppgavegrunnlag(
                    kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10_PROSENT,
                    oppgavetekst = "Avvik",
                ),
            ),
        ) shouldBe OppgaveopprettelseResultat.Opprettet(
            oppgaveId = oppgaveId,
            sakId = sak.sakId,
        )

        verify(oppgaveV2Client).opprettOppgaveMedSystembruker(any(), any(), any())
        verifyNoInteractions(oppgaveService)
        verifyNoMoreInteractions(oppgaveV2Client)
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

    private fun lagService(
        fradragssjekkRunPostgresRepo: FradragssjekkRunPostgresRepo = mock(),
        utbetalingsRepo: økonomi.domain.utbetaling.UtbetalingRepo = defaultMock(),
        oppgaveService: OppgaveService = defaultMock(),
        oppgaveV2Client: OppgaveV2Client = defaultMock(),
        brukOppgaveV2: Boolean = false,
    ): FradragsjobbenServiceImpl {
        return FradragsjobbenServiceImpl(
            aapKlient = defaultMock(),
            pesysKlient = defaultMock(),
            sakService = defaultMock(),
            oppgaveService = oppgaveService,
            oppgaveV2Client = oppgaveV2Client,
            brukOppgaveV2 = brukOppgaveV2,
            utbetalingsRepo = utbetalingsRepo,
            satsFactory = defaultMock(),
            fradragssjekkRunPostgresRepo = fradragssjekkRunPostgresRepo,
            clock = fixedClock,
        )
    }
}
