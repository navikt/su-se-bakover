package no.nav.su.se.bakover.database

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.string.shouldContain
import kotliquery.queryOf
import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException

internal class DatabaseRepoTest {

    private val repo = DatabaseRepo(EmbeddedDatabase.instance())
    private val FNR = FnrGenerator.random()

    @Test
    fun `combination of oppdragId and SakId should be unique`() {
        withMigratedDb {
            val sak = repo.opprettSak(FNR)
            shouldThrowExactly<PSQLException> {
                using(sessionOf(EmbeddedDatabase.instance())) {
                    val oppdragId = UUID30.randomUUID()
                    it.run(queryOf("insert into oppdrag (id, opprettet, sakId) values ('$oppdragId', now(), '${sak.id}')").asUpdate)
                }
            }.also {
                it.message shouldContain "duplicate key value violates unique constraint"
            }
        }
    }
    private fun insertSak(fnr: Fnr = FNR) = repo.opprettSak(fnr)
}
