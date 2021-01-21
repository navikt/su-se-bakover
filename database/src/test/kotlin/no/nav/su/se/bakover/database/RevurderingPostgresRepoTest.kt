package no.nav.su.se.bakover.database

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Test
import java.util.UUID

internal class RevurderingPostgresRepoTest {
    private val ds = EmbeddedDatabase.instance()
    private val behandlingRepo = BehandlingPostgresRepo(ds, BehandlingFactory(mock()))
    private val repo: RevurderingPostgresRepo = RevurderingPostgresRepo(ds, behandlingRepo)
    private val søknadRepo = SøknadPostgresRepo(ds)
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())

    @Test
    fun insert() {
        withMigratedDb {
            val behandling = setup()

            val inital = OpprettetRevurdering(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling
            )

            repo.lagre(inital)

            val hentet = repo.hent(inital.id)

            val newTime = Tidspunkt.now()
            val updated = inital.copy(opprettet = newTime)

            repo.lagre(updated)

            val hentetUpdated = repo.hent(updated.id)
            hentet shouldNotBe hentetUpdated
        }
    }

    private fun setup(): Behandling {
        val sak = testDataHelper.insertSak(FnrGenerator.random())
        val søknad = testDataHelper.insertSøknad(sak.id).let {
            søknadRepo.oppdaterOppgaveId(it.id, OppgaveId(""))
            søknadRepo.oppdaterjournalpostId(it.id, JournalpostId(""))
            søknadRepo.hentSøknad(it.id)
        }!!
        return testDataHelper.insertBehandling(
            sakId = sak.id,
            søknad = søknad,
            oppgaveId = OppgaveId("")
        ).let {
            behandlingRepo.hentBehandling(it.id)
        }!!
    }
}
