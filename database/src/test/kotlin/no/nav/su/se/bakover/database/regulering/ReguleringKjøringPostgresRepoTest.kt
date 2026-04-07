import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
            reguleringKjøringRepo.hent(reguleringKjøring.id) shouldBe reguleringKjøring
        }
    }

    private fun lagTestReguleringKjøring() = ReguleringKjøring(
        id = UUID.randomUUID(),
        aar = 2021,
        type = ReguleringKjøring.REGULERINGSTYPE_GRUNNBELØP,
        dryrun = true,
        startTid = LocalDateTime.of(2026, 1, 1, 12, 0),
        sakerAntall = 2,
        sakerIkkeLøpende = Json.parseToJsonElement("""["123","321"]""".trimIndent()).jsonArray,
        sakerAlleredeRegulert = Json.parseToJsonElement("""["123","321"]""".trimIndent()).jsonArray,
        sakerMåRevurderes = Json.parseToJsonElement("""["123","321"]""".trimIndent()).jsonArray,
        reguleringerSomFeilet = Json.parseToJsonElement("""["123","321"]""".trimIndent()).jsonArray,
        reguleringerAlleredeÅpen = Json.parseToJsonElement("""["123","321"]""".trimIndent()).jsonArray,
        reguleringerManuell = Json.parseToJsonElement("""["123","321"]""".trimIndent()).jsonArray,
        reguleringerAutomatisk = Json.parseToJsonElement("""["123","321"]""".trimIndent()).jsonArray,
    )
}
