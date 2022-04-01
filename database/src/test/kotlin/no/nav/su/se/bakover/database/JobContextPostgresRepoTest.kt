package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.JobContext
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test

internal class JobContextPostgresRepoTest {

    @Test
    fun `test`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).let { helper ->
                val context = JobContext.SendPåminnelseNyStønadsperiodeContext(fixedClock)
                val repo = helper.jobContextRepo

                helper.sessionFactory.withTransactionContext { tx ->
                    repo.lagre(context, tx)
                }

                repo.hent<JobContext.SendPåminnelseNyStønadsperiodeContext>(
                    JobContext.SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(fixedClock),
                ) shouldBe context

                repo.hent<JobContext.SendPåminnelseNyStønadsperiodeContext>(context.id()) shouldBe context

                helper.sessionFactory.withTransactionContext { tx ->
                    repo.lagre(context, tx)
                }

                val sendtOgProsessert = context.sendt(Saksnummer(2345))

                helper.sessionFactory.withTransactionContext { tx ->
                    repo.lagre(sendtOgProsessert, tx)
                }

                repo.hent<JobContext.SendPåminnelseNyStønadsperiodeContext>(context.id()) shouldBe sendtOgProsessert

                val prosessert = sendtOgProsessert.prosessert(Saksnummer(5432))

                helper.sessionFactory.withTransactionContext { tx ->
                    repo.lagre(prosessert, tx)
                }

                repo.hent<JobContext.SendPåminnelseNyStønadsperiodeContext>(context.id()) shouldBe prosessert
            }
        }
    }
}
