package no.nav.su.se.bakover.database.vedtak.snapshot

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.behandling.BehandlingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepoTest.Companion.defaultOversendtUtbetaling
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VedtakPostgresRepoTest {

    private val fnr = FnrGenerator.random()
    private val behandlingRepo = BehandlingPostgresRepo(EmbeddedDatabase.instance(), BehandlingFactory(mock()))
    private val repo = VedtakssnapshotPostgresRepo(EmbeddedDatabase.instance())
    private val testDataHelper = TestDataHelper()

    @Test
    fun `insert avslag`() {
        withMigratedDb {
            repo.opprettVedtakssnapshot(
                Vedtakssnapshot.Avslag(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    behandling = opprettBehandling(),
                    avslagsgrunner = listOf(Avslagsgrunn.PERSONLIG_OPPMØTE)
                )
            )
        }
    }

    @Test
    fun `insert innvilgelse`() {
        withMigratedDb {
            repo.opprettVedtakssnapshot(
                Vedtakssnapshot.Innvilgelse(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    behandling = opprettBehandling(),
                    utbetaling = defaultOversendtUtbetaling(UUID30.randomUUID(), fnr),
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
