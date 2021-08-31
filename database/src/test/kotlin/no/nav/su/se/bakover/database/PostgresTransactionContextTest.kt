package no.nav.su.se.bakover.database

import arrow.core.Either
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.test.external.ResultCaptor
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.postgresql.ds.PGSimpleDataSource
import java.sql.Connection
import java.time.temporal.ChronoUnit

internal class PostgresTransactionContextTest {
    val datasource = spy(EmbeddedDatabase.instance() as PGSimpleDataSource)
    private val testDataHelper = TestDataHelper(datasource)

    @Test
    fun `commits transaction and Starts and closes only one connection`() {
        withMigratedDb {
            val sak = testDataHelper.nySakMedNySøknad()
            reset(datasource)
            val resultCaptor = ResultCaptor<Connection>()
            Mockito.doAnswer(resultCaptor).whenever(datasource).connection
            testDataHelper.sessionFactory.withTransactionContext { context ->
                testDataHelper.søknadRepo.oppdaterSøknad(
                    sak.søknad.lukk(
                        lukketAv = NavIdentBruker.Saksbehandler("1"),
                        type = Søknad.Lukket.LukketType.TRUKKET,
                        lukketTidspunkt = fixedTidspunkt,
                    ),
                    context,
                )
                testDataHelper.søknadRepo.oppdaterSøknad(
                    sak.søknad.lukk(
                        lukketAv = NavIdentBruker.Saksbehandler("2"),
                        type = Søknad.Lukket.LukketType.AVVIST,
                        lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.DAYS),
                    ),
                    context,
                )
            }
            resultCaptor.result.size shouldBe 1
            resultCaptor.result[0]!!.isClosed shouldBe true
            testDataHelper.søknadRepo.hentSøknad(sak.søknad.id) shouldBe sak.søknad.lukk(
                lukketAv = NavIdentBruker.Saksbehandler("2"),
                type = Søknad.Lukket.LukketType.AVVIST,
                lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.DAYS),
            )
        }
    }

    @Test
    fun `throw should rollback`() {
        withMigratedDb {
            val sak = testDataHelper.nySakMedNySøknad()
            reset(datasource)
            val resultCaptor = ResultCaptor<Connection>()
            Mockito.doAnswer(resultCaptor).whenever(datasource).connection
            Either.catch {
                PostgresSessionFactory(datasource).withTransactionContext { context ->
                    testDataHelper.søknadRepo.oppdaterSøknad(
                        sak.søknad.lukk(
                            lukketAv = NavIdentBruker.Saksbehandler("1"),
                            type = Søknad.Lukket.LukketType.TRUKKET,
                            lukketTidspunkt = fixedTidspunkt,
                        ),
                        context,
                    )
                    testDataHelper.søknadRepo.oppdaterSøknad(
                        sak.søknad.lukk(
                            lukketAv = NavIdentBruker.Saksbehandler("2"),
                            type = Søknad.Lukket.LukketType.AVVIST,
                            lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.DAYS),
                        ),
                        context,
                    )
                    throw IllegalStateException("Throwing before transaction completes")
                }
            }
            resultCaptor.result.size shouldBe 1
            resultCaptor.result[0]!!.isClosed shouldBe true
            testDataHelper.søknadRepo.hentSøknad(sak.søknad.id) shouldBe sak.søknad
        }
    }
}
