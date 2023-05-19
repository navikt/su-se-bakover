package no.nav.su.se.bakover.common.infrastructure.persistence

import arrow.core.Either
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.persistence.withTestContext
import no.nav.su.se.bakover.test.persistence.withTransaction
import org.junit.jupiter.api.Test
import java.util.UUID

internal class PostgresTransactionContextTest {

    @Test
    fun `commits transaction and Starts and closes only one connection`() {
        withMigratedDb { dataSource ->
            withTestContext(dataSource, 1) { spiedDataSource ->
                spiedDataSource.withTransaction { tx ->
                    insertSak(tx)
                    insertSak(tx)
                    "select count(1) from sak".antall(session = tx) shouldBe 2
                }
            }
        }
    }

    @Test
    fun `throw should rollback`() {
        withMigratedDb { dataSource ->
            withTestContext(dataSource, 3) { spiedDataSource ->
                spiedDataSource.withSession { session ->
                    "select count(1) from sak".antall(session = session) shouldBe 0
                }
                Either.catch {
                    spiedDataSource.withTransaction { session ->
                        insertSak(session)
                        "select count(1) from sak".antall(session = session) shouldBe 1
                        throw IllegalStateException("Throwing before transaction completes")
                    }
                }
                spiedDataSource.withSession { session ->
                    "select count(1) from sak".antall(session = session) shouldBe 0
                }
            }
        }
    }

    private fun insertSak(session: Session) = run {
        "insert into sak (id, fnr, opprettet, type) values ('${UUID.randomUUID()}', '${Fnr.generer()}', '$fixedTidspunkt', 'UFØRE')".insert(
            emptyMap(),
            session,
        )
    }
}
