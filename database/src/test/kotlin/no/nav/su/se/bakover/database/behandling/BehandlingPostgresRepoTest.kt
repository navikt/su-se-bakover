package no.nav.su.se.bakover.database.behandling

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Test

val behandlingFactory = BehandlingFactory(mock())

internal class BehandlingPostgresRepoTest {

    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = BehandlingPostgresRepo(EmbeddedDatabase.instance(), behandlingFactory)
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")

    @Test
    fun `opprett og hent behandling`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak: Sak = testDataHelper.nySakMedJournalførtsøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            hentet shouldBe behandlingFactory.createBehandling(
                id = nySøknadsbehandling.id,
                opprettet = nySøknadsbehandling.opprettet,
                fnr = fnr,
                søknad = søknad,
                sakId = sak.id,
                oppgaveId = oppgaveId
            )
        }
    }

    @Test
    fun `oppdater behandlingsinformasjon`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak = testDataHelper.nySakMedJournalførtsøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = sak.søknader()[0].id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val oppdatert = repo.oppdaterBehandlingsinformasjon(
                nySøknadsbehandling.id,
                Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                        uføregrad = 40,
                        forventetInntekt = 200
                    )
                )
            )

            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            oppdatert.behandlingsinformasjon() shouldBe hentet.behandlingsinformasjon()
            nySøknadsbehandling.behandlingsinformasjon shouldNotBe hentet.behandlingsinformasjon()
        }
    }

    @Test
    fun `saksbehandle behandling`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak = testDataHelper.nySakMedJournalførtsøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = sak.søknader()[0].id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val saksbehandler = repo.settSaksbehandler(nySøknadsbehandling.id, NavIdentBruker.Saksbehandler("Per"))
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            hentet.saksbehandler() shouldBe saksbehandler.saksbehandler()
        }
    }

    @Test
    fun `attesterer behandling`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak = testDataHelper.nySakMedJournalførtsøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = sak.søknader()[0].id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val attestant = repo.attester(nySøknadsbehandling.id, NavIdentBruker.Attestant("kjella"))
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            hentet.attestant() shouldBe attestant.attestant()
        }
    }

    @Test
    fun `oppdater behandlingstatus`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak = testDataHelper.nySakMedJournalførtsøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = sak.søknader()[0].id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            nySøknadsbehandling.status shouldBe Behandling.BehandlingsStatus.OPPRETTET

            val oppdatertStatus =
                repo.oppdaterBehandlingStatus(nySøknadsbehandling.id, Behandling.BehandlingsStatus.BEREGNET_INNVILGET)
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)

            hentet!!.status() shouldBe oppdatertStatus.status()
        }
    }
}
