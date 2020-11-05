package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = SøknadPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb {
            EmbeddedDatabase.instance().withSession {
                val sak: Sak = testDataHelper.insertSak(FNR).toSak()
                val søknad: Søknad = Søknad(
                    sakId = sak.id,
                    id = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build()
                ).also { repo.opprettSøknad(it) }
                val hentet = repo.hentSøknad(søknad.id)

                søknad shouldBe hentet
            }
        }
    }

    @Test
    fun `nyopprettet søknad skal ikke være trukket`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.insertSak(FNR).toSak()
            val søknad: Søknad = Søknad(
                sakId = sak.id,
                id = UUID.randomUUID(),
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            ).also { repo.opprettSøknad(it) }
            val hentetSøknad: Søknad = repo.hentSøknad(søknad.id)!!
            hentetSøknad.id shouldBe søknad.id
            hentetSøknad.lukket shouldBe null
        }
    }

    @Test
    fun `trukket søknad skal bli hentet med saksbehandler som har trekt søknaden`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            val saksbehandler = Saksbehandler("Z993156")
            repo.lukkSøknad(
                søknadId = nySak.søknad.id,
                lukket = Søknad.Lukket(
                    tidspunkt = Tidspunkt.now(),
                    saksbehandler = saksbehandler.navIdent,
                    type = Søknad.LukketType.TRUKKET
                )
            )
            val hentetSøknad = repo.hentSøknad(nySak.søknad.id)
            hentetSøknad!!.id shouldBe nySak.søknad.id
            hentetSøknad.lukket shouldBe Søknad.Lukket(
                hentetSøknad.lukket!!.tidspunkt,
                saksbehandler.navIdent,
                Søknad.LukketType.TRUKKET
            )
        }
    }

    @Test
    fun `søknad har ikke påbegynt behandling`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            repo.harSøknadPåbegyntBehandling(nySak.søknad.id) shouldBe false
        }
    }

    @Test
    fun `søknad har påbegynt behandling`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            testDataHelper.insertBehandling(nySak.id, nySak.søknad)
            repo.harSøknadPåbegyntBehandling(nySak.søknad.id) shouldBe true
        }
    }

    @Test
    fun `lagrer oppgaveId`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            val oppgaveId = OppgaveId("1")
            repo.oppdaterOppgaveId(nySak.søknad.id, oppgaveId)
            EmbeddedDatabase.instance().withSession { session ->
                "select oppgaveId from søknad where id='${nySak.søknad.id}'".hentListe(
                    session = session
                ) { it.stringOrNull("oppgaveId") }
            } shouldBe listOf("1")
        }
    }

    @Test
    fun `lagrer journalPostId`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            val journalpostId = JournalpostId("2")
            repo.oppdaterjournalpostId(nySak.søknad.id, journalpostId)
            EmbeddedDatabase.instance().withSession { session ->
                "select journalpostId from søknad where id='${nySak.søknad.id}'".hentListe(
                    session = session
                ) { it.stringOrNull("journalpostId") }
            } shouldBe listOf("2")
        }
    }

    @Test
    fun `henter oppgave-id fra søknad`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            val oppgaveId = OppgaveId("1")
            repo.oppdaterOppgaveId(nySak.søknad.id, oppgaveId)
            repo.hentSøknad(nySak.søknad.id)?.oppgaveId shouldBe oppgaveId
        }
    }
}
