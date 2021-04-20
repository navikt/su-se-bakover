package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadsbehandlingServiceSimuleringTest {

    @Test
    fun `simuler behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn beregnetBehandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn simulertUtbetaling.right()
        }
        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(
            SøknadsbehandlingService.SimulerRequest(beregnetBehandling.id, saksbehandler)
        )

        val expected = Søknadsbehandling.Simulert(
            id = beregnetBehandling.id,
            opprettet = beregnetBehandling.opprettet,
            behandlingsinformasjon = beregnetBehandling.behandlingsinformasjon,
            søknad = beregnetBehandling.søknad,
            beregning = beregnetBehandling.beregning,
            sakId = beregnetBehandling.sakId,
            saksnummer = beregnetBehandling.saksnummer,
            fnr = beregnetBehandling.fnr,
            oppgaveId = beregnetBehandling.oppgaveId,
            simulering = simulering,
            fritekstTilBrev = "",
            stønadsperiode = beregnetBehandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        response shouldBe expected.right()

        verify(søknadsbehandlingRepoMock).hent(beregnetBehandling.id)
        verify(utbetalingServiceMock).simulerUtbetaling(
            sakId = argThat { it shouldBe beregnetBehandling.sakId },
            saksbehandler = argThat { it shouldBe saksbehandler },
            beregning = argThat { it shouldBe beregnetBehandling.beregning }
        )
        verify(søknadsbehandlingRepoMock).lagre(expected)
    }

    @Test
    fun `simuler behandling gir feilmelding hvis vi ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }
        val utbetalingServiceMock = mock<UtbetalingService>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(
            SøknadsbehandlingService.SimulerRequest(beregnetBehandling.id, saksbehandler)
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeSimulereBehandling.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe beregnetBehandling.id })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `simuler behandling gir feilmelding hvis simulering ikke går bra`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn beregnetBehandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(
            SøknadsbehandlingService.SimulerRequest(beregnetBehandling.id, saksbehandler)
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeSimulereBehandling.KunneIkkeSimulere.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe beregnetBehandling.id })

        verify(utbetalingServiceMock).simulerUtbetaling(
            sakId = argThat { it shouldBe beregnetBehandling.sakId },
            saksbehandler = argThat { it shouldBe saksbehandler },
            beregning = argThat { it shouldBe beregnetBehandling.beregning }
        )
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, utbetalingServiceMock)
    }

    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(0)
    private val fnr = Fnr("12345678910")
    private val saksbehandler = Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val stønadsperiode = ValgtStønadsperiode(Periode.create(1.januar(2021), 31.desember(2021)))
    private val beregnetBehandling = Søknadsbehandling.Beregnet.Innvilget(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = JournalpostId("j"),
        ),
        beregning = TestBeregning,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.EMPTY,
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(),
        nettoBeløp = 191500,
        periodeList = listOf()
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        sakId = sakId,
        saksnummer = saksnummer,
        utbetalingslinjer = listOf(),
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = Attestant("SU"),
        avstemmingsnøkkel = Avstemmingsnøkkel()
    )

    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)
}
