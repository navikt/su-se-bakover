import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

internal class ReguleringKjøringPostgresRepoTest {
    @Test
    fun `lagrer reguleringskjøring i databasen`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val reguleringKjøringRepo = testDataHelper.reguleringKjøringRepo
            val reguleringKjøring = lagTestReguleringKjøring()
            reguleringKjøringRepo.lagre(reguleringKjøring)
            hentReguleringKjøring(dataSource, reguleringKjøring.id) shouldBe reguleringKjøring
        }
    }

    private fun lagTestReguleringKjøring() = ReguleringKjøring(
        id = UUID.randomUUID(),
        aar = 2021,
        type = "dryrun",
        startTid = LocalDateTime.now(),
        antallProsesserteSaker = 10,
        antallReguleringerLaget = 5,
        antallKunneIkkeOpprettes = 2,
        arsakerReguleringIkkeOpprettet = "Feil",
        antallAutomatiskeReguleringer = 3,
        antallSupplementReguleringer = 1,
        antallReguleringerManuellBehandling = 1,
        arsakerManuellBehandling = "Manglende informasjon",
    )

    private fun hentReguleringKjøring(
        dataSource: DataSource,
        id: UUID,
    ): ReguleringKjøring? {
        val testDataHelper = TestDataHelper(dataSource)
        return testDataHelper.sessionFactory.withSession { session ->
            """
                select * from reguleringskjøring where id = :id
            """.trimIndent().hent(
                params = mapOf("id" to id),
                session = session,
            ) { row ->
                ReguleringKjøring(
                    id = row.uuid("id"),
                    aar = row.int("aar"),
                    type = row.string("type"),
                    startTid = row.localDateTime("start_tid"),
                    antallProsesserteSaker = row.int("antall_prosesserte_saker"),
                    antallReguleringerLaget = row.int("antall_reguleringer_laget"),
                    antallKunneIkkeOpprettes = row.int("antall_kunne_ikke_opprettes"),
                    arsakerReguleringIkkeOpprettet = row.string("arsaker_regulering_ikke_opprettet"),
                    antallAutomatiskeReguleringer = row.int("antall_automatiske_reguleringer"),
                    antallSupplementReguleringer = row.int("antall_supplement_reguleringer"),
                    antallReguleringerManuellBehandling = row.int("antall_reguleringer_manuell_behandling"),
                    arsakerManuellBehandling = row.string("arsaker_manuell_behandling"),
                )
            }
        }
    }
}
