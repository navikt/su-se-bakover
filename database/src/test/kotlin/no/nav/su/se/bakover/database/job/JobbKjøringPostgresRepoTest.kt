package no.nav.su.se.bakover.database.job

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.job.JobbKjøring
import no.nav.su.se.bakover.common.domain.job.JobbKjøringStatus
import no.nav.su.se.bakover.common.domain.job.JobbNavn
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class JobbKjøringPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer og henter en kjøring`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.databaseRepos.jobbKjøringRepo

        val kjøring = JobbKjøring.startet(jobbNavn = JobbNavn.JOURNALFØR_DOKUMENTER.visningsnavn, intervall = Duration.ofMinutes(1))
        repo.lagre(kjøring)

        val hentet = repo.hentSistePerJobb()
        hentet shouldHaveSize 1
        hentet[0].jobbNavn shouldBe JobbNavn.JOURNALFØR_DOKUMENTER.visningsnavn
        hentet[0].status shouldBe JobbKjøringStatus.KJØRER
        hentet[0].intervall shouldBe Duration.ofMinutes(1)
        hentet[0].ferdigTidspunkt shouldBe null
    }

    @Test
    fun `oppdaterer status til fullført`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.databaseRepos.jobbKjøringRepo

        val kjøring = JobbKjøring.startet(jobbNavn = JobbNavn.BESTILL_BREVDISTRIBUSJON.visningsnavn, intervall = Duration.ofMinutes(1))
        repo.lagre(kjøring)

        val fullført = kjøring.fullført()
        repo.oppdater(fullført)

        val hentet = repo.hentSistePerJobb()
        hentet shouldHaveSize 1
        hentet[0].status shouldBe JobbKjøringStatus.FULLFØRT
        hentet[0].ferdigTidspunkt shouldNotBe null
    }

    @Test
    fun `oppdaterer status til feilet med feilmelding`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.databaseRepos.jobbKjøringRepo

        val kjøring = JobbKjøring.startet(jobbNavn = JobbNavn.TILBAKEKREVING.visningsnavn, intervall = Duration.ofMinutes(10))
        repo.lagre(kjøring)

        val feilet = kjøring.feilet("Noe gikk galt")
        repo.oppdater(feilet)

        val hentet = repo.hentSistePerJobb()
        hentet shouldHaveSize 1
        hentet[0].status shouldBe JobbKjøringStatus.FEILET
        hentet[0].feilmelding shouldBe "Noe gikk galt"
        hentet[0].ferdigTidspunkt shouldNotBe null
    }

    @Test
    fun `hentSistePerJobb returnerer kun siste kjøring per jobbtype`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.databaseRepos.jobbKjøringRepo

        val første = JobbKjøring.startet(jobbNavn = JobbNavn.GRENSESNITTSAVSTEMMING.visningsnavn, intervall = Duration.ofMinutes(5))
        repo.lagre(første)
        repo.oppdater(første.fullført())

        Thread.sleep(10)

        val andre = JobbKjøring.startet(jobbNavn = JobbNavn.GRENSESNITTSAVSTEMMING.visningsnavn, intervall = Duration.ofMinutes(5))
        repo.lagre(andre)
        repo.oppdater(andre.feilet("timeout"))

        val jobbB = JobbKjøring.startet(jobbNavn = JobbNavn.KONSISTENSAVSTEMMING.visningsnavn, intervall = Duration.ofMinutes(5))
        repo.lagre(jobbB)
        repo.oppdater(jobbB.fullført())

        val hentet = repo.hentSistePerJobb()
        hentet shouldHaveSize 2

        val avstemmingResult = hentet.first { it.jobbNavn == JobbNavn.GRENSESNITTSAVSTEMMING.visningsnavn }
        avstemmingResult.status shouldBe JobbKjøringStatus.FEILET
        avstemmingResult.id shouldBe andre.id

        val konsistensResult = hentet.first { it.jobbNavn == JobbNavn.KONSISTENSAVSTEMMING.visningsnavn }
        konsistensResult.status shouldBe JobbKjøringStatus.FULLFØRT
    }
}
