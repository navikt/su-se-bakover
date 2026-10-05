package no.nav.su.se.bakover.database.kontrollsamtalenotat

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.kontrollsamtale.KontrollsamtaleNotatPostgresRepo
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class KontrollsamtaleNotatPostgresRepoTest(
    private val dataSource: DataSource,
) {
    @Test
    fun `oppdaterer journalpostId kun første gang`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = KontrollsamtaleNotatPostgresRepo(
            testDataHelper.sessionFactory,
            testDataHelper.dbMetrics,
        )
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()

        val kontrollsamtaleNotat = KontrollsamtaleNotat(
            id = UUID.randomUUID(),
            sakId = sak.id,
            opprettet = Tidspunkt.now(testDataHelper.clock),
            personligOppmøte = true,
            fullmaktOgLegeerklæring = null,
            originalPass = true,
            gyldigPass = true,
            harVærtUtenlands = false,
            utenlandsoppholdDatoer = emptyList(),
            harPlanerOmUtenlandsreise = false,
            planlagteUtenlandsreiseDatoer = emptyList(),
            reiseDokumentasjon = false,
            økonomiskSituasjon = false,
            andreForhold = false,
            skatteOpplysninger = false,
            fritekst = null,
        )
        repo.lagre(
            kontrollsamtaleNotat,
            sak.id,
        )

        repo.hentKontrollsamtaleNotat(sak.id)!!.journalpostId shouldBe null

        val førsteJournalpostId = JournalpostId("førsteJournalpostId")
        val andreJournalpostId = JournalpostId("andreJournalpostId")

        repo.oppdaterJournalpostId(
            kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
            journalpostId = førsteJournalpostId,
        ) shouldBe true

        repo.hentKontrollsamtaleNotat(sak.id)!!.journalpostId shouldBe førsteJournalpostId

        repo.oppdaterJournalpostId(
            kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
            journalpostId = andreJournalpostId,
        ) shouldBe false

        repo.hentKontrollsamtaleNotat(sak.id)!!.journalpostId shouldBe førsteJournalpostId
    }

    @Test
    fun `lagrer samme notat-ID bare en gang og beholder innhold og journalpost-ID`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = KontrollsamtaleNotatPostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val notat = kontrollnotat(sak.id, Tidspunkt.now(testDataHelper.clock))
        val journalpostId = JournalpostId("journalpostId")

        repo.lagre(notat, sak.id) shouldBe true
        repo.oppdaterJournalpostId(notat.id, journalpostId) shouldBe true

        repo.lagre(notat.copy(fritekst = "Annet innhold"), sak.id) shouldBe false
        repo.hentForId(notat.id) shouldBe notat.copy(journalpostId = journalpostId)
        repo.hentUtenJournalpostId().filter { it.sakId == sak.id } shouldBe emptyList()
    }

    @Test
    fun `samme notat-ID kan ikke lagres på en annen sak`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = KontrollsamtaleNotatPostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        val førsteSak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val andreSak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val notat = kontrollnotat(førsteSak.id, Tidspunkt.now(testDataHelper.clock))

        repo.lagre(notat, førsteSak.id) shouldBe true
        repo.lagre(notat.copy(sakId = andreSak.id), andreSak.id) shouldBe false

        repo.hentForId(notat.id) shouldBe notat
        repo.hentKontrollsamtaleNotat(andreSak.id) shouldBe null
    }

    @Test
    fun `forskjellige notat-ID-er kan lagres på samme sak`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = KontrollsamtaleNotatPostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val førsteNotat = kontrollnotat(sak.id, Tidspunkt.now(testDataHelper.clock))
        val andreNotat = førsteNotat.copy(id = UUID.randomUUID())

        repo.lagre(førsteNotat, sak.id) shouldBe true
        repo.lagre(andreNotat, sak.id) shouldBe true

        repo.hentForId(førsteNotat.id) shouldBe førsteNotat
        repo.hentForId(andreNotat.id) shouldBe andreNotat
        repo.hentUtenJournalpostId().filter { it.sakId == sak.id }.toSet() shouldBe setOf(førsteNotat, andreNotat)
    }

    private fun kontrollnotat(sakId: UUID, opprettet: Tidspunkt) = KontrollsamtaleNotat(
        id = UUID.randomUUID(),
        sakId = sakId,
        opprettet = opprettet,
        personligOppmøte = true,
        fullmaktOgLegeerklæring = null,
        originalPass = true,
        gyldigPass = true,
        harVærtUtenlands = false,
        utenlandsoppholdDatoer = emptyList(),
        harPlanerOmUtenlandsreise = false,
        planlagteUtenlandsreiseDatoer = emptyList(),
        reiseDokumentasjon = false,
        økonomiskSituasjon = false,
        andreForhold = false,
        skatteOpplysninger = false,
        fritekst = null,
    )
}
