package no.nav.su.se.bakover.database.notat

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.notat.Notat
import no.nav.su.se.bakover.domain.notat.NotatHandling
import no.nav.su.se.bakover.domain.notat.NotatHendelse
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class NotatRepoTest(private val dataSource: DataSource) {

    private val clock = Clock.systemUTC()

    @Test
    fun `opprett og hent notat`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.notatRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val nå = Tidspunkt.now(clock)

        val notat = Notat(
            id = UUID.randomUUID(),
            sakId = sak.id,
            referanseId = UUID.randomUUID(),
            notat = "Dette er et testnotat",
            opprettet = nå,
            endret = nå,
            hendelser = listOf(
                NotatHendelse(
                    navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                    tidspunkt = nå,
                    handling = NotatHandling.OPPRETTET,
                ),
            ),
        )

        repo.opprett(notat)
        val hentet = repo.hent(notat.id)
        hentet shouldBe notat
    }

    @Test
    fun `oppdater notat`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.notatRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val nå = Tidspunkt.now(clock)

        val notat = Notat(
            id = UUID.randomUUID(),
            sakId = sak.id,
            referanseId = UUID.randomUUID(),
            notat = "Originalt notat",
            opprettet = nå,
            endret = nå,
            hendelser = listOf(
                NotatHendelse(
                    navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                    tidspunkt = nå,
                    handling = NotatHandling.OPPRETTET,
                ),
            ),
        )
        repo.opprett(notat)

        val oppdatert = notat.copy(
            notat = "Oppdatert notat",
            endret = Tidspunkt.now(clock),
            hendelser = notat.hendelser + NotatHendelse(
                navIdent = NavIdentBruker.Saksbehandler("Z654321"),
                tidspunkt = Tidspunkt.now(clock),
                handling = NotatHandling.OPPDATERT,
            ),
        )
        repo.oppdater(oppdatert)

        val hentet = repo.hent(notat.id)
        hentet shouldBe oppdatert
    }

    @Test
    fun `hentForSak returnerer alle notater for sak`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.notatRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val nå = Tidspunkt.now(clock)

        val notat1 = Notat(
            id = UUID.randomUUID(),
            sakId = sak.id,
            referanseId = UUID.randomUUID(),
            notat = "Notat 1",
            opprettet = nå,
            endret = nå,
            hendelser = listOf(
                NotatHendelse(
                    navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                    tidspunkt = nå,
                    handling = NotatHandling.OPPRETTET,
                ),
            ),
        )
        val notat2 = Notat(
            id = UUID.randomUUID(),
            sakId = sak.id,
            referanseId = UUID.randomUUID(),
            notat = "Notat 2",
            opprettet = nå,
            endret = nå,
            hendelser = listOf(
                NotatHendelse(
                    navIdent = NavIdentBruker.Saksbehandler("Z654321"),
                    tidspunkt = nå,
                    handling = NotatHandling.OPPRETTET,
                ),
            ),
        )

        repo.opprett(notat1)
        repo.opprett(notat2)

        val hentet = repo.hentForSak(sak.id)
        hentet shouldContainExactlyInAnyOrder listOf(notat1, notat2)
    }

    @Test
    fun `hentForSak returnerer tom liste for sak uten notater`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.notatRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()

        repo.hentForSak(sak.id) shouldBe emptyList()
    }

    @Test
    fun `NotatHandling enum lagres og leses korrekt`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.notatRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val nå = Tidspunkt.now(clock)

        NotatHandling.entries.forEach { handling ->
            val notat = Notat(
                id = UUID.randomUUID(),
                sakId = sak.id,
                referanseId = UUID.randomUUID(),
                notat = "Notat med handling $handling",
                opprettet = nå,
                endret = nå,
                hendelser = listOf(
                    NotatHendelse(
                        navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                        tidspunkt = nå,
                        handling = handling,
                    ),
                ),
            )
            repo.opprett(notat)
            val hentet = repo.hent(notat.id)!!
            hentet.hendelser.single().handling shouldBe handling
        }
    }
}
