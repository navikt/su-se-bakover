package no.nav.su.se.bakover.database.søknad

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val søknadRepo = SøknadPostgresRepo(EmbeddedDatabase.instance())
    private val sakRepo = SakPostgresRepo(EmbeddedDatabase.instance(), mock(), mock())

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb {
            EmbeddedDatabase.instance().withSession {
                testDataHelper.insertSak(FNR)
                val sak: Sak = sakRepo.hentSak(FNR)!!
                val søknad: Søknad = Søknad.Ny(
                    sakId = sak.id,
                    id = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                    opprettet = Tidspunkt.EPOCH,
                ).also { søknadRepo.opprettSøknad(it) }
                val hentet = søknadRepo.hentSøknad(søknad.id)

                søknad shouldBe hentet
            }
        }
    }

    @Test
    fun `nyopprettet søknad skal ikke være trukket`() {
        withMigratedDb {
            testDataHelper.insertSak(FNR)
            val sak: Sak = sakRepo.hentSak(FNR)!!
            val søknad: Søknad = Søknad.Ny(
                sakId = sak.id,
                id = UUID.randomUUID(),
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                opprettet = Tidspunkt.EPOCH,
            ).also { søknadRepo.opprettSøknad(it) }
            val hentetSøknad: Søknad = søknadRepo.hentSøknad(søknad.id)!!
            hentetSøknad.id shouldBe søknad.id
            hentetSøknad.shouldNotBeTypeOf<Søknad.Lukket>()
        }
    }

    @Test
    fun `lagrer og henter lukket søknad`() {
        val lukketBrevbestillingId = BrevbestillingId("lukketBrevbestillingId")
        val lukketJournalpostId = JournalpostId("lukketJournalpostId")
        val nySøknadJournalpostId = JournalpostId("nySøknadJournalpostId")
        val nySøknadOppgaveId = OppgaveId("nySøknadOppgaveId")

        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            val søknad: Søknad.Ny = nySak.søknad
            val saksbehandler = Saksbehandler("Z993156")
            val journalførtSøknadMedOppgave = søknad.journalfør(nySøknadJournalpostId).medOppgave(nySøknadOppgaveId)
            søknadRepo.oppdaterOppgaveId(søknad.id, nySøknadOppgaveId)
            søknadRepo.oppdaterjournalpostId(søknad.id, nySøknadJournalpostId)
            val lukketSøknad = journalførtSøknadMedOppgave
                .lukk(
                    lukketAv = saksbehandler,
                    type = Søknad.Lukket.LukketType.TRUKKET,
                    lukketTidspunkt = Tidspunkt.EPOCH
                )
                .medBrevbestillingId(lukketBrevbestillingId)
                .medJournalpostId(lukketJournalpostId)
            søknadRepo.oppdaterSøknad(lukketSøknad)
            val hentetSøknad = søknadRepo.hentSøknad(nySak.søknad.id)!!
            hentetSøknad shouldBe lukketSøknad
        }
    }

    @Test
    fun `søknad har ikke påbegynt behandling`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            søknadRepo.harSøknadPåbegyntBehandling(nySak.søknad.id) shouldBe false
        }
    }

    @Test
    fun `søknad har påbegynt behandling`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            testDataHelper.insertBehandling(nySak.id, nySak.søknad)
            søknadRepo.harSøknadPåbegyntBehandling(nySak.søknad.id) shouldBe true
        }
    }

    @Test
    fun `lagrer journalPostId`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            val journalpostId = JournalpostId("2")
            søknadRepo.oppdaterjournalpostId(nySak.søknad.id, journalpostId)
            EmbeddedDatabase.instance().withSession { session ->
                "select journalpostId from søknad where id='${nySak.søknad.id}'".hentListe(
                    session = session
                ) { it.stringOrNull("journalpostId") }
            } shouldBe listOf("2")
        }
    }

    @Test
    fun `lagrer og henter oppgaveId fra søknad`() {
        withMigratedDb {
            val oppgaveId = OppgaveId("o")
            val journalpostId = JournalpostId("j")
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(FNR, oppgaveId, journalpostId)
            val søknadId = sak.søknader()[0].id
            søknadRepo.oppdaterOppgaveId(søknadId, oppgaveId)
            val hentetSøknad = søknadRepo.hentSøknad(søknadId)!!
            hentetSøknad.shouldBeTypeOf<Søknad.Journalført.MedOppgave>()
            hentetSøknad.oppgaveId shouldBe oppgaveId
        }
    }

    @Test
    fun `hent søknader uten journalpost`() {
        withMigratedDb {
            val journalpostId = JournalpostId("j")
            val sak = testDataHelper.insertSak(FNR)
            testDataHelper.insertSøknad(sak.id).journalfør(journalpostId).let {
                søknadRepo.oppdaterjournalpostId(it.id, it.journalpostId)
            }
            testDataHelper.insertSøknad(sak.id).lukk(
                lukketAv = Saksbehandler("saksbehandler"),
                type = Søknad.Lukket.LukketType.TRUKKET,
                lukketTidspunkt = Tidspunkt.EPOCH
            ).let {
                søknadRepo.oppdaterSøknad(it)
            }
            søknadRepo.hentSøknaderUtenJournalpost() shouldBe listOf(
                sak.søknad
            )
        }
    }

    @Test
    fun `hent søknader med journalpost men uten oppgave`() {
        withMigratedDb {
            val journalpostId = JournalpostId("j")
            val journalpostId2 = JournalpostId("j2")
            val oppgaveId = OppgaveId("o")
            val sak = testDataHelper.insertSak(FNR)
            val journalførtSøknad = testDataHelper.insertSøknad(sak.id).journalfør(journalpostId).also { journalførtSøknad ->
                søknadRepo.oppdaterjournalpostId(journalførtSøknad.id, journalførtSøknad.journalpostId)
            }
            testDataHelper.insertSøknad(sak.id).journalfør(journalpostId2).let { journalførtSøknad2 ->
                søknadRepo.oppdaterjournalpostId(journalførtSøknad2.id, journalførtSøknad2.journalpostId)
                journalførtSøknad2.medOppgave(oppgaveId).also { søknadMedOppgave ->
                    søknadRepo.oppdaterOppgaveId(søknadMedOppgave.id, søknadMedOppgave.oppgaveId)
                }
            }
            testDataHelper.insertSøknad(sak.id).lukk(
                lukketAv = Saksbehandler("saksbehandler"),
                type = Søknad.Lukket.LukketType.TRUKKET,
                lukketTidspunkt = Tidspunkt.EPOCH
            ).let {
                søknadRepo.oppdaterSøknad(it)
            }
            søknadRepo.hentSøknaderMedJournalpostMenUtenOppgave() shouldBe listOf(
                journalførtSøknad
            )
        }
    }
}
