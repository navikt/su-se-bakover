package no.nav.su.se.bakover.database

import arrow.core.Either
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

internal class PostgresTransactionContextTest {

    @Test
    fun `commits transaction and Starts and closes only one connection`() {
        withMigratedDb { dataSource ->
            val (_, søknad) = TestDataHelper(dataSource).persisterJournalførtSøknadMedOppgave()
            withTestContext(dataSource, 1) { spiedDataSource ->
                val testDataHelper = TestDataHelper(spiedDataSource)
                testDataHelper.sessionFactory.withTransactionContext { context ->
                    testDataHelper.søknadRepo.lukkSøknad(
                        søknad.lukk(
                            lukketAv = NavIdentBruker.Saksbehandler("1"),
                            type = Søknad.Journalført.MedOppgave.Lukket.LukketType.TRUKKET,
                            lukketTidspunkt = fixedTidspunkt,
                        ),
                        context,
                    )
                    testDataHelper.søknadRepo.lukkSøknad(
                        søknad.lukk(
                            lukketAv = NavIdentBruker.Saksbehandler("2"),
                            type = Søknad.Journalført.MedOppgave.Lukket.LukketType.AVVIST,
                            lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.DAYS),
                        ),
                        context,
                    )
                }
            }
            TestDataHelper(dataSource).søknadRepo.hentSøknad(søknad.id) shouldBe søknad.lukk(
                lukketAv = NavIdentBruker.Saksbehandler("2"),
                type = Søknad.Journalført.MedOppgave.Lukket.LukketType.AVVIST,
                lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.DAYS),
            )
        }
    }

    @Test
    fun `throw should rollback`() {
        withMigratedDb { dataSource ->
            val (_, søknad) = TestDataHelper(dataSource).persisterJournalførtSøknadMedOppgave()

            withTestContext(dataSource, 1) { spiedDataSource ->
                val testDataHelper = TestDataHelper(spiedDataSource)
                Either.catch {
                    PostgresSessionFactory(
                        dataSource = spiedDataSource,
                        dbMetrics = dbMetricsStub,
                        sessionCounter = sessionCounterStub,
                    ).withTransactionContext { context ->
                        testDataHelper.søknadRepo.lukkSøknad(
                            søknad.lukk(
                                lukketAv = NavIdentBruker.Saksbehandler("1"),
                                type = Søknad.Journalført.MedOppgave.Lukket.LukketType.TRUKKET,
                                lukketTidspunkt = fixedTidspunkt,
                            ),
                            context,
                        )
                        testDataHelper.søknadRepo.lukkSøknad(
                            søknad.lukk(
                                lukketAv = NavIdentBruker.Saksbehandler("2"),
                                type = Søknad.Journalført.MedOppgave.Lukket.LukketType.AVVIST,
                                lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.DAYS),
                            ),
                            context,
                        )
                        throw IllegalStateException("Throwing before transaction completes")
                    }
                }
            }
            TestDataHelper(dataSource).søknadRepo.hentSøknad(søknad.id) shouldBe søknad
        }
    }
}
