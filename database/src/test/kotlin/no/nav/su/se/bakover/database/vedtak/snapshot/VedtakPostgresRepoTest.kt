package no.nav.su.se.bakover.database.vedtak.snapshot

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.behandling.BehandlingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepoTest.Companion.defaultOversendtUtbetaling
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VedtakPostgresRepoTest {

    private val fnr = FnrGenerator.random()
    private val saksnummer = Saksnummer(1234)
    private val behandlingRepo = BehandlingPostgresRepo(EmbeddedDatabase.instance(), BehandlingFactory(mock()))
    private val repo = VedtakssnapshotPostgresRepo(EmbeddedDatabase.instance())
    private val testDataHelper = TestDataHelper()

    @Test
    fun `insert avslag`() {
        withMigratedDb {
            val behandling = opprettBehandling().copy(
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
            )
            repo.opprettVedtakssnapshot(
                Vedtakssnapshot.Avslag(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    behandling = behandling,
                    avslagsgrunner = listOf(Avslagsgrunn.PERSONLIG_OPPMØTE)
                )
            )
        }
    }

    @Test
    fun `insert innvilgelse`() {
        withMigratedDb {
            val behandling = opprettBehandling().copy(
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
            )
            repo.opprettVedtakssnapshot(
                Vedtakssnapshot.Innvilgelse(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    behandling = behandling,
                    utbetaling = defaultOversendtUtbetaling(saksnummer = saksnummer, fnr = fnr),
                )
            )
        }
    }

    private fun opprettBehandling(): Behandling {

        val oppgaveId = OppgaveId("o")
        val journalpostId = JournalpostId("j")
        val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
        val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
        val nySøknadsbehandling = NySøknadsbehandling(
            sakId = sak.id,
            søknadId = søknad.id,
            oppgaveId = oppgaveId
        )

        behandlingRepo.opprettSøknadsbehandling(nySøknadsbehandling)
        return behandlingRepo.hentBehandling(nySøknadsbehandling.id)!!
    }
}
