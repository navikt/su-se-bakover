package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeTypeOf
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
                val søknad: Søknad = Søknad.Ny(
                    sakId = sak.id,
                    id = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
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
            val søknad: Søknad = Søknad.Ny(
                sakId = sak.id,
                id = UUID.randomUUID(),
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            ).also { repo.opprettSøknad(it) }
            val hentetSøknad: Søknad = repo.hentSøknad(søknad.id)!!
            hentetSøknad.id shouldBe søknad.id
            hentetSøknad.shouldNotBeTypeOf<Søknad.Lukket>()
        }
    }

    @Test
    fun `trukket søknad skal bli hentet med saksbehandler som har trekt søknaden`() {
        withMigratedDb {
            val nySak: NySak = testDataHelper.insertSak(FNR)
            val søknad: Søknad.Ny = nySak.søknad
            val saksbehandler = Saksbehandler("Z993156")
            val lukketSøknad = søknad.lukk(
                av = saksbehandler,
                type = Søknad.Lukket.LukketType.TRUKKET
            )
            repo.lukkSøknad(lukketSøknad)
            val hentetSøknad = repo.hentSøknad(nySak.søknad.id)!!
            hentetSøknad shouldBe lukketSøknad
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
    fun `lagrer og henter oppgaveId fra søknad`() {
        withMigratedDb {
            val oppgaveId = OppgaveId("o")
            val journalpostId = JournalpostId("j")
            val sak = testDataHelper.nySakMedJournalførtsøknadOgOppgave(FNR, oppgaveId, journalpostId)
            val søknadId = sak.søknader()[0].id
            repo.oppdaterOppgaveId(søknadId, oppgaveId)
            val hentetSøknad = repo.hentSøknad(søknadId)!!
            hentetSøknad.shouldBeTypeOf<Søknad.Journalført.MedOppgave>()
            hentetSøknad.oppgaveId shouldBe oppgaveId
        }
    }
}
