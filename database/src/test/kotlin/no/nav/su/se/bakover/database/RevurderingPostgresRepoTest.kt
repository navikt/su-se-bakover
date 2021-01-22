package no.nav.su.se.bakover.database

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingPostgresRepo
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BeregnetRevurdering
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
    fun `overskriver ikke permanente verdier ved lagring`() {
        withMigratedDb {
            val behandling = setupBehandling()

            val original = OpprettetRevurdering(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling
            )

            repo.lagre(original)

            val førOppdatering = repo.hent(original.id)

            val kopiMedNyttTidspunkt = original.copy(
                opprettet = Tidspunkt.now()
            )

            repo.lagre(kopiMedNyttTidspunkt)

            val etterOppdatering = repo.hent(original.id)

            førOppdatering shouldBe etterOppdatering
        }
    }

    @Test
    fun `overskriver tidligere persisterte verdier når man går til en tidligere tilstand`() {
        withMigratedDb {
            val behandling = setupBehandling()

            val original = BeregnetRevurdering(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling,
                beregning = TestBeregning
            )

            repo.lagre(original)

            val førOppdatering = repo.hent(original.id)!!

            val nyttObjektMedSammeId = OpprettetRevurdering(
                id = original.id,
                opprettet = original.opprettet,
                tilRevurdering = original.tilRevurdering
            )

            repo.lagre(nyttObjektMedSammeId)

            val etterOppdatering = repo.hent(original.id)!!

            førOppdatering should beOfType(BeregnetRevurdering::class)
            etterOppdatering should beOfType(OpprettetRevurdering::class)
        }
    }

    private fun setupBehandling(): Behandling {
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
