package no.nav.su.se.bakover.database.behandling

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.beregning.assertBeregningMapping
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
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
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
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
                søknad = søknad,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = fnr,
                oppgaveId = oppgaveId
            )
        }
    }

    @Test
    fun `oppdater behandlingsinformasjon`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = sak.søknader()[0].id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val oppdatertBehandlingsinformasjon = Behandlingsinformasjon(
                uførhet = Behandlingsinformasjon.Uførhet(
                    status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    uføregrad = 40,
                    forventetInntekt = 200,
                    begrunnelse = null
                )
            ).also {
                repo.oppdaterBehandlingsinformasjon(nySøknadsbehandling.id, it)
            }

            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            oppdatertBehandlingsinformasjon shouldBe hentet.behandlingsinformasjon()
            nySøknadsbehandling.behandlingsinformasjon shouldNotBe hentet.behandlingsinformasjon()
        }
    }

    @Test
    fun `saksbehandle behandling`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = sak.søknader()[0].id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val saksbehandler = NavIdentBruker.Saksbehandler("Per")
            repo.settSaksbehandler(nySøknadsbehandling.id, saksbehandler)
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            hentet.saksbehandler() shouldBe saksbehandler
        }
    }

    @Test
    fun `hentIverksatteBehandlingerUtenJournalposteringer`() {
        withMigratedDb {

            val iverksattBehandling = FnrGenerator.random().let {
                val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(it)
                val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
                testDataHelper.insertBehandling(sak.id, søknad, søknad.oppgaveId).also {
                    repo.oppdaterBehandlingStatus(it.id, Behandling.BehandlingsStatus.IVERKSATT_INNVILGET)
                }
            }

            FnrGenerator.random().let {
                val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(it)
                val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
                testDataHelper.insertBehandling(sak.id, søknad, søknad.oppgaveId)
            }

            val actual = repo.hentIverksatteBehandlingerUtenJournalposteringer()
            actual shouldBe listOf(
                repo.hentBehandling(iverksattBehandling.id)
            )
        }
    }

    @Test
    fun `attesterer behandling`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = sak.søknader()[0].id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val attestant = NavIdentBruker.Attestant("kjella")
            repo.oppdaterAttestering(nySøknadsbehandling.id, Attestering.Iverksatt(attestant))
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            hentet.attestering()?.attestant shouldBe attestant
        }
    }

    @Test
    fun `attestant underkjenner saksbehandling`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = sak.søknader()[0].id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val attestant = NavIdentBruker.Attestant("kjella")
            val underkjennelse = Attestering.Underkjent(
                attestant = attestant,
                grunn = Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
                kommentar = "1+1 er ikke 3"
            )
            repo.oppdaterAttestering(
                nySøknadsbehandling.id,
                underkjennelse
            )
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!.attestering() as Attestering.Underkjent

            hentet.attestant shouldBe attestant
            hentet.grunn shouldBe Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL
            hentet.kommentar shouldBe "1+1 er ikke 3"
        }
    }

    @Test
    fun `oppdater behandlingstatus`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = sak.søknader()[0].id,
                oppgaveId = oppgaveId
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            nySøknadsbehandling.status shouldBe Behandling.BehandlingsStatus.OPPRETTET

            val oppdatertStatus = Behandling.BehandlingsStatus.BEREGNET_INNVILGET
            repo.oppdaterBehandlingStatus(nySøknadsbehandling.id, oppdatertStatus)
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)

            hentet!!.status() shouldBe oppdatertStatus
        }
    }

    @Test
    fun `legger til og henter ut beregning`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id,
                oppgaveId = oppgaveId
            )
            repo.opprettSøknadsbehandling(nySøknadsbehandling)
            repo.leggTilBeregning(nySøknadsbehandling.id, TestBeregning)
            assertBeregningMapping(repo.hentBehandling(nySøknadsbehandling.id)!!.beregning()!!, TestBeregning)
        }
    }

    @Test
    fun `sletter beregning`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id,
                oppgaveId = oppgaveId
            )
            repo.opprettSøknadsbehandling(nySøknadsbehandling)
            repo.leggTilBeregning(nySøknadsbehandling.id, TestBeregning)
            repo.hentBehandling(nySøknadsbehandling.id)!!.beregning() shouldNotBe null
            repo.slettBeregning(nySøknadsbehandling.id)
            repo.hentBehandling(nySøknadsbehandling.id)!!.beregning() shouldBe null
        }
    }

    @Test
    fun `oppdaterer oppgaveid`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id,
                oppgaveId = oppgaveId
            )
            repo.opprettSøknadsbehandling(nySøknadsbehandling)
            val oppdatertOppgaveId = OppgaveId("ny")
            repo.oppdaterOppgaveId(nySøknadsbehandling.id, oppdatertOppgaveId)
            repo.hentBehandling(nySøknadsbehandling.id)!!.oppgaveId() shouldBe oppdatertOppgaveId
        }
    }

    @Test
    fun `oppdaterer iverksattJournalpostId`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id,
                oppgaveId = oppgaveId
            )
            repo.opprettSøknadsbehandling(nySøknadsbehandling)
            val iverksattJournalpostId = JournalpostId("ny")
            repo.oppdaterIverksattJournalpostId(nySøknadsbehandling.id, iverksattJournalpostId)
            repo.hentBehandling(nySøknadsbehandling.id)!!.iverksattJournalpostId() shouldBe iverksattJournalpostId
        }
    }

    @Test
    fun `oppdaterer iverksattBrevbestillingId`() {
        withMigratedDb {
            val fnr = FnrGenerator.random()
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr, oppgaveId, journalpostId)
            val søknad = sak.søknader()[0] as Søknad.Journalført.MedOppgave
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id,
                oppgaveId = oppgaveId
            )
            repo.opprettSøknadsbehandling(nySøknadsbehandling)
            val iverksattBrevbestillingId = BrevbestillingId("ny")
            repo.oppdaterIverksattBrevbestillingId(nySøknadsbehandling.id, iverksattBrevbestillingId)
            repo.hentBehandling(nySøknadsbehandling.id)!!.iverksattBrevbestillingId() shouldBe iverksattBrevbestillingId
        }
    }
}
