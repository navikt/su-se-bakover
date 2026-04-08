import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class ReguleringKjøringPostgresRepoTest {
    @Test
    fun `lagrer reguleringskjøring i databasen`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val reguleringKjøringRepo = testDataHelper.reguleringKjøringRepo
            val reguleringKjøring = lagTestReguleringKjøring()
            reguleringKjøringRepo.lagre(reguleringKjøring)
            val result = reguleringKjøringRepo.hent()
            result.size shouldBe 1
            result.single() shouldBe reguleringKjøring
        }
    }

    private fun lagTestReguleringKjøring() = ReguleringKjøring(
        id = UUID.randomUUID(),
        aar = 2021,
        type = ReguleringKjøring.REGULERINGSTYPE_GRUNNBELØP,
        dryrun = true,
        startTid = LocalDateTime.of(2026, 1, 1, 12, 0),
        sakerAntall = 2,
        sakerIkkeLøpende = listOf("123", "321"),
        sakerAlleredeRegulert = listOf("123", "321"),
        sakerMåRevurderes = listOf("123", "321"),
        reguleringerSomFeilet = listOf("123", "321"),
        reguleringerAlleredeÅpen = listOf("123", "321"),
        reguleringerManuell = listOf("123", "321"),
        reguleringerAutomatisk = listOf("123", "321"),
    )
}
