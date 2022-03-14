package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeTypeOf
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.TestDataHelper.Companion.journalførtSøknad
import no.nav.su.se.bakover.database.TestDataHelper.Companion.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTestContext
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test

internal class SøknadPostgresRepoTest {

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadRepo = testDataHelper.søknadRepo
            dataSource.withSession {
                val sak: NySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
                val nySøknad: Søknad = testDataHelper.persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(sak.id)
                søknadRepo.hentSøknad(nySøknad.id) shouldBe nySøknad
            }
        }
    }

    @Test
    fun `nyopprettet søknad skal ikke være trukket`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadRepo = testDataHelper.søknadRepo
            val sak: NySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            val nySøknad: Søknad = testDataHelper.persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(sak.id)
            søknadRepo.hentSøknad(nySøknad.id)!!.shouldNotBeTypeOf<Søknad.Journalført.MedOppgave.Lukket>()
        }
    }

    @Test
    fun `lagrer og henter lukket søknad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nySak: Sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            val journalførtSøknadMedOppgave: Søknad.Journalført.MedOppgave.IkkeLukket =
                nySak.journalførtSøknadMedOppgave()
            val saksbehandler = Saksbehandler("Z993156")
            val lukketSøknad = journalførtSøknadMedOppgave
                .lukk(
                    lukketAv = saksbehandler,
                    type = Søknad.Journalført.MedOppgave.Lukket.LukketType.TRUKKET,
                    lukketTidspunkt = fixedTidspunkt,
                )
            withTestContext(dataSource, 2) { spiedDataSource ->
                val søknadRepo = TestDataHelper(spiedDataSource).søknadRepo
                søknadRepo.lukkSøknad(lukketSøknad)
                val hentetSøknad = søknadRepo.hentSøknad(journalførtSøknadMedOppgave.id)!!
                hentetSøknad shouldBe lukketSøknad
            }
        }
    }

    @Test
    fun `lagrer journalPostId`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadRepo = testDataHelper.søknadRepo
            fun hentJournalpostId(nySak: NySak): List<String?> {
                return dataSource.withSession { session ->
                    "select journalpostId from søknad where id='${nySak.søknad.id}'".hentListe(
                        session = session,
                    ) { it.stringOrNull("journalpostId") }
                }
            }

            val journalpostId = JournalpostId("oppdatertJournalpostId")
            val nySak: NySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            hentJournalpostId(nySak) shouldBe emptyList()
            val søknad = nySak.søknad.journalfør(journalpostId)
            søknadRepo.oppdaterjournalpostId(søknad)
            hentJournalpostId(nySak) shouldBe listOf(journalpostId.toString())
        }
    }

    @Test
    fun `lagrer og henter oppgaveId fra søknad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadRepo = testDataHelper.søknadRepo
            val sak = testDataHelper.persisterSakOgJournalførtSøknadUtenOppgave().first
            val journalførtSøknadMedOppgave = sak.journalførtSøknad().medOppgave(OppgaveId("oppdatertOppgaveId"))
            søknadRepo.oppdaterOppgaveId(journalførtSøknadMedOppgave)
            val hentetSøknad = søknadRepo.hentSøknad(journalførtSøknadMedOppgave.id)!!
            hentetSøknad.shouldBeTypeOf<Søknad.Journalført.MedOppgave.IkkeLukket>()
            hentetSøknad shouldBe journalførtSøknadMedOppgave
        }
    }

    @Test
    fun `hent søknader uten journalpost`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadRepo = testDataHelper.søknadRepo
            val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            testDataHelper.persisterSakOgJournalførtSøknadUtenOppgave()
            testDataHelper.persisterLukketJournalførtSøknadMedOppgave(sak.id)
            søknadRepo.hentSøknaderUtenJournalpost() shouldBe listOf(
                sak.søknad,
            )
        }
    }

    @Test
    fun `hent søknader med journalpost men uten oppgave`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadRepo = testDataHelper.søknadRepo
            val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            val journalførtSøknad = testDataHelper.persisterJournalførtSøknadUtenOppgaveForEksisterendeSak(sak.id)
            testDataHelper.persisterJournalførtSøknadMedOppgave(sak.id)
            testDataHelper.persisterLukketJournalførtSøknadMedOppgave(sak.id)
            søknadRepo.hentSøknaderMedJournalpostMenUtenOppgave() shouldBe listOf(
                journalførtSøknad,
            )
        }
    }
}
