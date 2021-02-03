package no.nav.su.se.bakover.database.vedtak.snapshot

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepoTest.Companion.lagUtbetalingUtenKvittering
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class VedtakPostgresRepoTest {

    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    private val fnr = FnrGenerator.random()
    private val saksnummer = Saksnummer(1234)
    private val søknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(EmbeddedDatabase.instance())
    private val utbetalingRepo = UtbetalingPostgresRepo(EmbeddedDatabase.instance())
    private val repo = VedtakssnapshotPostgresRepo(EmbeddedDatabase.instance())
    private val testDataHelper = TestDataHelper(clock = fixedClock)
    private val opprettet = Tidspunkt.now(fixedClock)
    private val oppgaveId = OppgaveId("oppgaveId")
    private val journalpostId = JournalpostId("journalpostId")
    private val attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant"))
    private val beregning = TestBeregning
    private val simulering = Simulering(
        gjelderId = FnrGenerator.random(),
        gjelderNavn = "gjelderNavn",
        datoBeregnet = opprettet.toLocalDate(ZoneOffset.UTC),
        nettoBeløp = 0,
        periodeList = listOf()
    )
    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")

    @Test
    fun `insert avslag`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
            val nySøknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Avslag(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                sakId = sak.id,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                    .withAlleVilkårOppfylt(),
                fnr = fnr,
            ).also {
                søknadsbehandlingRepo.lagre(it)
            }
            val avslagUtenBeregning = nySøknadsbehandling
                .tilAttestering(saksbehandler)
                .tilIverksatt(
                    attestering,
                    Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.JournalførtOgDistribuertBrev(
                        journalpostId = JournalpostId("iverksattJournalpostId"),
                        brevbestillingId = BrevbestillingId("brevbestillingId")
                    )
                ).also {
                    søknadsbehandlingRepo.lagre(it)
                }

            repo.opprettVedtakssnapshot(
                Vedtakssnapshot.Avslag(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    søknadsbehandling = avslagUtenBeregning.also {
                        søknadsbehandlingRepo.lagre(it)
                    },
                    avslagsgrunner = listOf(Avslagsgrunn.PERSONLIG_OPPMØTE),
                )
            )
        }
    }

    @Test
    fun `insert innvilgelse`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave

            val nySøknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Innvilget(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                sakId = sak.id,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                    .withAlleVilkårOppfylt(),
                fnr = fnr,
            ).also {
                søknadsbehandlingRepo.lagre(it)
            }
            val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
                id = UUID30.randomUUID(),
                opprettet = opprettet,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                simulering = simulering,
                utbetalingsrequest = Utbetalingsrequest("utbetalingsRequest"),
                utbetalingslinjer = emptyList(),
                fnr = sak.fnr,
                type = Utbetaling.UtbetalingsType.NY,
                behandler = NavIdentBruker.Saksbehandler("Z123")
            )
            utbetalingRepo.opprettUtbetaling(utbetaling)
            val innvilget = nySøknadsbehandling
                .tilBeregnet(beregning)
                .tilSimulert(simulering)
                .tilAttestering(saksbehandler)
                .tilIverksatt(attestering, utbetaling.id).also {
                    søknadsbehandlingRepo.lagre(it)
                }
            repo.opprettVedtakssnapshot(
                vedtakssnapshot = Vedtakssnapshot.Innvilgelse(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    søknadsbehandling = innvilget,
                    utbetaling = lagUtbetalingUtenKvittering(saksnummer = saksnummer, fnr = fnr),
                )
            )
        }
    }
}
