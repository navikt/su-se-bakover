package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

internal class JobContextPostgresRepoTest {

    @Test
    fun `diverse inserts og updates`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).let { helper ->
                val context = SendPåminnelseNyStønadsperiodeContext(fixedClock)
                val repo = helper.databaseRepos.jobContextRepo

                helper.sessionFactory.withTransactionContext { tx ->
                    repo.lagre(context, tx)
                }

                repo.hent<SendPåminnelseNyStønadsperiodeContext>(
                    SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(fixedClock),
                ) shouldBe context

                repo.hent<SendPåminnelseNyStønadsperiodeContext>(context.id()) shouldBe context

                helper.sessionFactory.withTransactionContext { tx ->
                    repo.lagre(context, tx)
                }

                helper.sessionFactory.withTransactionContext { tx ->
                    repo.lagre(
                        context.copy(
                            prosessert = setOf(Saksnummer(2345)),
                            sendt = setOf(Saksnummer(2345)),
                        ),
                        tx,
                    )
                }

                repo.hent<SendPåminnelseNyStønadsperiodeContext>(context.id())!!.let {
                    it.prosessert() shouldBe setOf(Saksnummer(2345))
                    it.sendt() shouldBe setOf(Saksnummer(2345))
                }
            }
        }
    }
}
