package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeTypeOf
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.fixedClock
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.TestDataHelper.Companion.journalførtSøknad
import no.nav.su.se.bakover.test.persistence.TestDataHelper.Companion.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.persistence.withTestContext
import no.nav.su.se.bakover.test.trekkSøknad
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class SøknadPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `opprett og hent søknad`() {
        val testDataHelper = TestDataHelper(dataSource)
        val søknadRepo = testDataHelper.søknadRepo
        dataSource.withSession {
            val sak: NySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            val nySøknad: Søknad = testDataHelper.persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(
                sakId = sak.id,
                fnr = sak.fnr,
            )
            søknadRepo.hentSøknad(nySøknad.id) shouldBe nySøknad
        }
    }

    @Test
    fun `nyopprettet søknad skal ikke være trukket`() {
        val testDataHelper = TestDataHelper(dataSource)
        val søknadRepo = testDataHelper.søknadRepo
        val sak: NySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val nySøknad: Søknad =
            testDataHelper.persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(sakId = sak.id, fnr = sak.fnr)
        søknadRepo.hentSøknad(nySøknad.id)!!.shouldNotBeTypeOf<Søknad.Journalført.MedOppgave.Lukket>()
    }

    @Test
    fun `lagrer og henter lukket søknad`() {
        val testDataHelper = TestDataHelper(dataSource)
        val nySak: Sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
        val journalførtSøknadMedOppgave: Søknad.Journalført.MedOppgave.IkkeLukket =
            nySak.journalførtSøknadMedOppgave()
        val saksbehandler = Saksbehandler("Z993156")
        val lukketSøknad = journalførtSøknadMedOppgave
            .let {
                it.lukk(
                    trekkSøknad(
                        søknadId = it.id,
                        saksbehandler = saksbehandler,
                        lukketTidspunkt = Tidspunkt.now(testDataHelper.clock),
                    ),
                )
            }
        withTestContext(dataSource, 2) { spiedDataSource ->
            val søknadRepo = TestDataHelper(spiedDataSource).søknadRepo
            søknadRepo.lukkSøknad(lukketSøknad)
            val hentetSøknad = søknadRepo.hentSøknad(journalførtSøknadMedOppgave.id)!!
            hentetSøknad shouldBe lukketSøknad
        }
    }

    @Test
    fun `lagrer journalPostId`() {
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

    @Test
    fun `lagrer og henter oppgaveId fra søknad`() {
        val testDataHelper = TestDataHelper(dataSource)
        val søknadRepo = testDataHelper.søknadRepo
        val sak = testDataHelper.persisterSakOgJournalførtSøknadUtenOppgave().first
        val journalførtSøknadMedOppgave = sak.journalførtSøknad().medOppgave(OppgaveId("oppdatertOppgaveId"))
        søknadRepo.oppdaterOppgaveId(journalførtSøknadMedOppgave)
        val hentetSøknad = søknadRepo.hentSøknad(journalførtSøknadMedOppgave.id)!!
        hentetSøknad.shouldBeTypeOf<Søknad.Journalført.MedOppgave.IkkeLukket>()
        hentetSøknad shouldBe journalførtSøknadMedOppgave
    }

    @Test
    fun `hent søknader uten journalpost`() {
        val testDataHelper = TestDataHelper(dataSource)
        val søknadRepo = testDataHelper.søknadRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        testDataHelper.persisterSakOgJournalførtSøknadUtenOppgave()
        testDataHelper.persisterLukketJournalførtSøknadMedOppgave(sak.id)
        søknadRepo.hentSøknaderUtenJournalpost() shouldBe listOf(
            sak.søknad,
        )
    }

    @Test
    fun `hent søknader med journalpost men uten oppgave`() {
        val testDataHelper = TestDataHelper(dataSource)
        val søknadRepo = testDataHelper.søknadRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val journalførtSøknad = testDataHelper.persisterJournalførtSøknadUtenOppgaveForEksisterendeSak(
            sakId = sak.id,
            fnr = sak.fnr,
        )
        testDataHelper.persisterJournalførtSøknadMedOppgave(sak.id)
        testDataHelper.persisterLukketJournalførtSøknadMedOppgave(sak.id)
        søknadRepo.hentSøknaderMedJournalpostMenUtenOppgave() shouldBe listOf(
            journalførtSøknad,
        )
    }

    @Test
    fun `henter søknader uten behandling uavhengig av journalpost oppgave og lukking`() {
        val testDataHelper = TestDataHelper(dataSource)
        val søknadRepo = testDataHelper.søknadRepo
        val (sak, søknadUtenBehandling) = testDataHelper.persisterJournalførtSøknadMedOppgave()
        val nySøknad = testDataHelper.persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(sakId = sak.id, fnr = sak.fnr)
        val journalførtSøknad = testDataHelper.persisterJournalførtSøknadUtenOppgaveForEksisterendeSak(sakId = sak.id, fnr = sak.fnr)
        val lukketSøknad = testDataHelper.persisterLukketJournalførtSøknadMedOppgave(sakId = sak.id, fnr = sak.fnr)
        val sakOgSøknadMedBehandling = testDataHelper.persisterJournalførtSøknadMedOppgave(sakId = sak.id, fnr = sak.fnr)
        testDataHelper.persisterSøknadsbehandlingVilkårsvurdert(sakOgSøknad = sakOgSøknadMedBehandling)

        søknadRepo.hentSøknaderUtenBehandling(Tidspunkt.now(testDataHelper.clock).plus(1, ChronoUnit.MICROS)).toSet() shouldBe setOf(
            søknadUtenBehandling,
            nySøknad,
            journalførtSøknad,
            lukketSøknad,
        )
    }

    @Test
    fun `henter bare søknader opprettet før tidsgrensen`() {
        val opprettetFør = Tidspunkt.parse("2021-01-01T12:00:00Z")
        val eldreSøknad = TestDataHelper(dataSource, clock = opprettetFør.minus(1, ChronoUnit.MICROS).fixedClock())
            .persisterJournalførtSøknadMedOppgave().second
        val testDataHelper = TestDataHelper(dataSource, clock = opprettetFør.fixedClock())
        testDataHelper.persisterJournalførtSøknadMedOppgave()
        TestDataHelper(dataSource, clock = opprettetFør.plus(1, ChronoUnit.MICROS).fixedClock())
            .persisterJournalførtSøknadMedOppgave()

        testDataHelper.søknadRepo.hentSøknaderUtenBehandling(opprettetFør) shouldBe listOf(eldreSøknad)
    }
}
