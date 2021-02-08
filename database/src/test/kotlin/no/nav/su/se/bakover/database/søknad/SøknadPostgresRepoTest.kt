package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeTypeOf
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.TestDataHelper.Companion.journalførtSøknad
import no.nav.su.se.bakover.database.TestDataHelper.Companion.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Test

internal class SøknadPostgresRepoTest {

    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val søknadRepo = SøknadPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb {
            EmbeddedDatabase.instance().withSession {
                val sak: NySak = testDataHelper.nySakMedNySøknad()
                val nySøknad: Søknad = testDataHelper.nySøknadForEksisterendeSak(sak.id)
                søknadRepo.hentSøknad(nySøknad.id) shouldBe nySøknad
            }
        }
    }

    @Test
    fun `nyopprettet søknad skal ikke være trukket`() {
        withMigratedDb {
            val sak: NySak = testDataHelper.nySakMedNySøknad()
            val nySøknad: Søknad = testDataHelper.nySøknadForEksisterendeSak(sak.id)
            søknadRepo.hentSøknad(nySøknad.id)!!.shouldNotBeTypeOf<Søknad.Lukket>()
        }
    }

    @Test
    fun `lagrer og henter lukket søknad`() {
        val lukketBrevbestillingId = BrevbestillingId("lukketBrevbestillingId")
        val lukketJournalpostId = JournalpostId("lukketJournalpostId")

        withMigratedDb {
            val nySak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val journalførtSøknadMedOppgave: Søknad.Journalført.MedOppgave = nySak.journalførtSøknadMedOppgave()
            val saksbehandler = Saksbehandler("Z993156")
            val lukketSøknad = journalførtSøknadMedOppgave
                .lukk(
                    lukketAv = saksbehandler,
                    type = Søknad.Lukket.LukketType.TRUKKET,
                    lukketTidspunkt = fixedTidspunkt
                )
                .medBrevbestillingId(lukketBrevbestillingId)
                .medJournalpostId(lukketJournalpostId)
            søknadRepo.oppdaterSøknad(lukketSøknad)
            val hentetSøknad = søknadRepo.hentSøknad(journalførtSøknadMedOppgave.id)!!
            hentetSøknad shouldBe lukketSøknad
        }
    }

    @Test
    fun `søknad har ikke påbegynt behandling`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.nySakMedNySøknad()
            søknadRepo.harSøknadPåbegyntBehandling(nySak.søknad.id) shouldBe false
        }
    }

    @Test
    fun `søknad har påbegynt behandling`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val søknad = sak.journalførtSøknadMedOppgave()
            testDataHelper.nyUavklartVilkårsvurdering(sak, søknad)
            søknadRepo.harSøknadPåbegyntBehandling(søknad.id) shouldBe true
        }
    }

    @Test
    fun `lagrer journalPostId`() {
        withMigratedDb {
            fun hentJournalpostId(nySak: NySak): List<String?> {
                return EmbeddedDatabase.instance().withSession { session ->
                    "select journalpostId from søknad where id='${nySak.søknad.id}'".hentListe(
                        session = session
                    ) { it.stringOrNull("journalpostId") }
                }
            }

            val journalpostId = JournalpostId("oppdatertJournalpostId")
            val nySak: NySak = testDataHelper.nySakMedNySøknad()
            hentJournalpostId(nySak) shouldBe emptyList()
            val søknad = nySak.søknad.journalfør(journalpostId)
            søknadRepo.oppdaterjournalpostId(søknad)
            hentJournalpostId(nySak) shouldBe listOf(journalpostId.toString())
        }
    }

    @Test
    fun `lagrer og henter oppgaveId fra søknad`() {
        withMigratedDb {
            val sak = testDataHelper.nySakMedJournalførtSøknad()
            val journalførtSøknadMedOppgave = sak.journalførtSøknad().medOppgave(OppgaveId("oppdatertOppgaveId"))
            søknadRepo.oppdaterOppgaveId(journalførtSøknadMedOppgave)
            val hentetSøknad = søknadRepo.hentSøknad(journalførtSøknadMedOppgave.id)!!
            hentetSøknad.shouldBeTypeOf<Søknad.Journalført.MedOppgave>()
            hentetSøknad shouldBe journalførtSøknadMedOppgave
        }
    }

    @Test
    fun `hent søknader uten journalpost`() {
        withMigratedDb {
            val sak = testDataHelper.nySakMedNySøknad()
            testDataHelper.nySakMedJournalførtSøknad()
            testDataHelper.nyLukketSøknadForEksisterendeSak(sak.id)
            søknadRepo.hentSøknaderUtenJournalpost() shouldBe listOf(
                sak.søknad
            )
        }
    }

    @Test
    fun `hent søknader med journalpost men uten oppgave`() {
        withMigratedDb {
            val sak = testDataHelper.nySakMedNySøknad()
            val journalførtSøknad = testDataHelper.journalførtSøknadForEksisterendeSak(sak.id)
            testDataHelper.journalførtSøknadMedOppgaveForEksisterendeSak(sak.id)
            testDataHelper.nyLukketSøknadForEksisterendeSak(sak.id)
            søknadRepo.hentSøknaderMedJournalpostMenUtenOppgave() shouldBe listOf(
                journalførtSøknad
            )
        }
    }
}
