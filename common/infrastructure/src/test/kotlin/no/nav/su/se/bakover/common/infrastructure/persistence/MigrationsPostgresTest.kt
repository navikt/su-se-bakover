package no.nav.su.se.bakover.common.infrastructure.persistence

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.withSession
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class MigrationsPostgresTest(private val dataSource: DataSource) {

    @Test
    fun `rader skal ikke lekke ut av withMigratedDb`() {
        fun insert(session: Session) = run {
            """
                     insert into sak (id, fnr, opprettet, type) values ('${UUID.randomUUID()}', '${Fnr.generer()}', '$fixedTidspunkt', 'UFØRE')
            """.trimIndent().insert(emptyMap(), session)
        }
        dataSource.withSession { session ->
            insert(session)
            "select count(1) from sak".antall(session = session) shouldBe 1
        }

        // This test expects data to be cleared between test runs, which is handled by DbExtension
        dataSource.withSession { session ->
            "select count(1) from sak".antall(session = session) shouldBe 0
        }
    }
}
