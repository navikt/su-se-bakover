package no.nav.su.se.bakover.database.jobcontext

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.database.stønadsperiode.SendPåminnelseNyStønadsperiodeJobPostgresRepo
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class StønadsperiodePostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `diverse inserts og updates`() {
        TestDataHelper(dataSource).let { helper ->
            val context = SendPåminnelseNyStønadsperiodeContext(fixedClock)
            val repo = SendPåminnelseNyStønadsperiodeJobPostgresRepo(
                JobContextPostgresRepo(helper.sessionFactory),
            )

            helper.sessionFactory.withTransactionContext { tx ->
                repo.lagre(context, tx)
            }

            repo.hent(
                SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(fixedClock),
            ) shouldBe context

            repo.hent(context.id()) shouldBe context

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

            repo.hent(context.id())!!.let {
                it.prosessert() shouldBe setOf(Saksnummer(2345))
                it.sendt() shouldBe setOf(Saksnummer(2345))
            }
        }
    }
}
