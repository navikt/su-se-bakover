package no.nav.su.se.bakover.database.søknad

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.TestDataHelper.Companion.journalførtSøknad
import no.nav.su.se.bakover.database.TestDataHelper.Companion.journalførtSøknadMedOppgave
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
    private val sakRepo = SakPostgresRepo(EmbeddedDatabase.instance(), mock())

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb {
            EmbeddedDatabase.instance().withSession {
                testDataHelper.nySakMedNySøknad(FNR)
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
            testDataHelper.nySakMedNySøknad(FNR)
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

        withMigratedDb {
            val nySak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(FNR)
            val journalførtSøknadMedOppgave: Søknad.Journalført.MedOppgave = nySak.journalførtSøknadMedOppgave()
            val saksbehandler = Saksbehandler("Z993156")
            val lukketSøknad = journalførtSøknadMedOppgave
                .lukk(
                    lukketAv = saksbehandler,
                    type = Søknad.Lukket.LukketType.TRUKKET,
                    lukketTidspunkt = Tidspunkt.EPOCH
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
            val nySak: NySak = testDataHelper.nySakMedNySøknad(FNR)
            søknadRepo.harSøknadPåbegyntBehandling(nySak.søknad.id) shouldBe false
        }
    }

    @Test
    fun `søknad har påbegynt behandling`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave(fnr = FNR)
            val søknad = sak.journalførtSøknadMedOppgave()
            testDataHelper.uavklartVilkårsvurdering(sak, søknad)
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
            val nySak: NySak = testDataHelper.nySakMedNySøknad(FNR)
            hentJournalpostId(nySak) shouldBe emptyList()
            val søknad = nySak.søknad.journalfør(journalpostId)
            søknadRepo.oppdaterjournalpostId(søknad)
            hentJournalpostId(nySak) shouldBe listOf(journalpostId.toString())
        }
    }

    @Test
    fun `lagrer og henter oppgaveId fra søknad`() {
        withMigratedDb {
            val sak = testDataHelper.nySakMedJournalførtSøknad(FNR)
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
            val journalpostId = JournalpostId("j")
            val sak = testDataHelper.nySakMedNySøknad(FNR)
            testDataHelper.insertSøknad(sak.id).journalfør(journalpostId).let {
                søknadRepo.oppdaterjournalpostId(it)
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
            val sak = testDataHelper.nySakMedNySøknad(FNR)
            val journalførtSøknad =
                testDataHelper.insertSøknad(sak.id).journalfør(journalpostId).also { journalførtSøknad ->
                    søknadRepo.oppdaterjournalpostId(journalførtSøknad)
                }
            testDataHelper.insertSøknad(sak.id).journalfør(journalpostId2).let { journalførtSøknad2 ->
                søknadRepo.oppdaterjournalpostId(journalførtSøknad2)
                journalførtSøknad2.medOppgave(oppgaveId).also { søknadMedOppgave ->
                    søknadRepo.oppdaterOppgaveId(søknadMedOppgave)
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
