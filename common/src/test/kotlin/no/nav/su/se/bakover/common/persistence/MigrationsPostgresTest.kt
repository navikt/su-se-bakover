package no.nav.su.se.bakover.common.persistence

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import org.junit.jupiter.api.Test
import java.util.UUID

internal class MigrationsPostgresTest {

    @Test
    fun `rader skal ikke lekke ut av withMigratedDb`() {
        fun insert(session: Session) = run {
            """
                     insert into sak (id, fnr, opprettet, type) values ('${UUID.randomUUID()}', '${Fnr.generer()}', '$fixedTidspunkt', 'UFØRE')
            """.trimIndent().insert(emptyMap(), session)
        }
        withMigratedDb { dataSource ->

            dataSource.withSession { session ->
                insert(session)
                "select count(1) from sak".antall(session = session) shouldBe 1
            }
        }
        withMigratedDb { dataSource ->
            dataSource.withSession { session ->
                "select count(1) from sak".antall(session = session) shouldBe 0
            }
        }
    }
}
