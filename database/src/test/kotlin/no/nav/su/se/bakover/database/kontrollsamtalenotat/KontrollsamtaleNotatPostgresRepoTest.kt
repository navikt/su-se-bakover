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
}
