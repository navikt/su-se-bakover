package no.nav.su.se.bakover.database.notat

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.notat.Notat
import no.nav.su.se.bakover.domain.notat.NotatHandling
import no.nav.su.se.bakover.domain.notat.NotatHendelse
import no.nav.su.se.bakover.domain.notat.ReferanseType
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withSession
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
            referanseType = ReferanseType.SØKNAD,
        )

        repo.opprett(notat)
        val hentet = repo.hent(notat.id)
        hentet shouldBe notat
    }

    @Test
    fun `opprett og hent notat for referanseid og type`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.notatRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val nå = Tidspunkt.now(clock)

        val referanseId = UUID.randomUUID()
        val notat = Notat(
            id = UUID.randomUUID(),
            sakId = sak.id,
            referanseId = referanseId,
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
            referanseType = ReferanseType.SØKNAD,
        )

        repo.opprett(notat)
        val hentet = repo.hentForReferanse(referanseId, ReferanseType.SØKNAD)
        hentet shouldBe notat
    }

    @Test
    fun `opprett og hent notat bevarer rolle for notathendelser`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.notatRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val nå = Tidspunkt.now(clock)

        val notat = Notat(
            id = UUID.randomUUID(),
            sakId = sak.id,
            referanseId = UUID.randomUUID(),
            notat = "Testnotat",
            opprettet = nå,
            endret = nå,
            referanseType = ReferanseType.SØKNAD,
            hendelser = listOf(
                NotatHendelse(
                    navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                    tidspunkt = nå,
                    handling = NotatHandling.OPPRETTET,
                ),
            ),
        )

        repo.opprett(notat)

        val oppdatertAvSaksbehandler = notat.copy(
            notat = "Oppdatert av saksbehandler",
            endret = Tidspunkt.now(clock),
            hendelser = notat.hendelser + NotatHendelse(
                navIdent = NavIdentBruker.Saksbehandler("Z654321"),
                tidspunkt = Tidspunkt.now(clock),
                handling = NotatHandling.OPPDATERT,
            ),
        )
        repo.oppdaterNotatSaksbehandler(oppdatertAvSaksbehandler)

        val oppdatertAvAttestant = oppdatertAvSaksbehandler.copy(
            attestantNotat = "Oppdatert av attestant",
            endret = Tidspunkt.now(clock),
            hendelser = oppdatertAvSaksbehandler.hendelser + NotatHendelse(
                navIdent = NavIdentBruker.Attestant("Z654322"),
                tidspunkt = Tidspunkt.now(clock),
                handling = NotatHandling.OPPDATERT,
            ),
        )
        repo.oppdaterAttestantNotat(oppdatertAvAttestant)

        val hentet = repo.hent(notat.id)!!
        hentet.hendelser[0].navIdent shouldBe NavIdentBruker.Saksbehandler("Z123456")
        hentet.hendelser[1].navIdent shouldBe NavIdentBruker.Saksbehandler("Z654321")
        hentet.hendelser[2].navIdent shouldBe NavIdentBruker.Attestant("Z654322")
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
            referanseType = ReferanseType.SØKNAD,
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
        repo.oppdaterNotatSaksbehandler(oppdatert)

        val hentet = repo.hent(notat.id)!!
        hentet.attestantNotat shouldBe oppdatert.attestantNotat
        hentet.referanseType shouldBe oppdatert.referanseType
        hentet.notat shouldBe oppdatert.notat
        hentet.hendelser.last().handling shouldBe NotatHandling.OPPDATERT
        hentet.hendelser.last().navIdent shouldBe NavIdentBruker.Saksbehandler("Z654321")
    }

    @Test
    fun `oppdater attestant notat`() {
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
            referanseType = ReferanseType.SØKNAD,
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
            attestantNotat = "Oppdatert attestantnotat",
            endret = Tidspunkt.now(clock),
            hendelser = notat.hendelser + NotatHendelse(
                navIdent = NavIdentBruker.Attestant("Z654321"),
                tidspunkt = Tidspunkt.now(clock),
                handling = NotatHandling.OPPDATERT,
            ),
        )
        repo.oppdaterAttestantNotat(oppdatert)

        val hentet = repo.hent(notat.id)!!
        hentet.attestantNotat shouldBe oppdatert.attestantNotat
        hentet.notat shouldBe oppdatert.notat
        hentet.referanseType shouldBe oppdatert.referanseType
        hentet.hendelser.last().handling shouldBe NotatHandling.OPPDATERT
        hentet.hendelser.last().navIdent shouldBe NavIdentBruker.Attestant("Z654321")
    }

    @Test
    fun `oppdater notat med vedleggshendelse bevarer hvasomerEndret`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.notatRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val nå = Tidspunkt.now(clock)

        val notat = Notat(
            id = UUID.randomUUID(),
            sakId = sak.id,
            referanseId = UUID.randomUUID(),
            notat = "Notat med vedleggshendelse",
            opprettet = nå,
            endret = nå,
            referanseType = ReferanseType.SØKNAD,
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
            endret = Tidspunkt.now(clock),
            hendelser = notat.hendelser + NotatHendelse(
                navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                tidspunkt = Tidspunkt.now(clock),
                handling = NotatHandling.VEDLEGG_LAGT_TIL,
                hvasomerEndret = "test.pdf",
            ),
        )

        repo.oppdaterNotatSaksbehandler(oppdatert)

        val hentet = repo.hent(notat.id)!!
        hentet.hendelser.last().hvasomerEndret shouldBe "test.pdf"
    }

    @Test
    fun `hent notat med null i attestant notat mapes til tom streng`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.notatRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val nå = Tidspunkt.now(clock)

        val notat = Notat(
            id = UUID.randomUUID(),
            sakId = sak.id,
            referanseId = UUID.randomUUID(),
            notat = "Originalt notat",
            attestantNotat = "Skal nulles ut i databasen",
            opprettet = nå,
            endret = nå,
            referanseType = ReferanseType.SØKNAD,
            hendelser = listOf(
                NotatHendelse(
                    navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                    tidspunkt = nå,
                    handling = NotatHandling.OPPRETTET,
                ),
            ),
        )
        repo.opprett(notat)

        dataSource.withSession { session ->
            """
            UPDATE notat
            SET attestant_notat = NULL
            WHERE id = :id
            """.trimIndent().oppdatering(
                mapOf("id" to notat.id),
                session,
            )
        }

        val hentet = repo.hent(notat.id)!!
        hentet.attestantNotat shouldBe ""
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
            referanseType = ReferanseType.SØKNAD,
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
            referanseType = ReferanseType.SØKNAD,
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
                referanseType = ReferanseType.SØKNAD,
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
