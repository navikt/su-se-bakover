import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
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
        type = ReguleringKjøringType.DRYRUN.name,
        startTid = LocalDateTime.of(2026, 1, 1, 12, 0),
        antallProsesserteSaker = 10,
        antallReguleringerLaget = 5,
        antallKunneIkkeOpprettes = 2,
        arsakerReguleringIkkeOpprettet = "Feil",
        antallAutomatiskeReguleringer = 3,
        antallSupplementReguleringer = 1,
        antallReguleringerManuellBehandling = 1,
        arsakerManuellBehandling = "Manglende informasjon",
    )
}
