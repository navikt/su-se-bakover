package no.nav.su.se.bakover.database.avstemming

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

internal class AvstemmingPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = AvstemmingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `henter siste avstemming`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FnrGenerator.random())
            val utbetaling = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())

            val zero = repo.hentSisteAvstemming()
            zero shouldBe null

            repo.opprettAvstemming(
                Avstemming(
                    fom = 1.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                    tom = 2.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                    utbetalinger = listOf(utbetaling)
                )
            )

            val second = repo.opprettAvstemming(
                Avstemming(
                    fom = 3.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                    tom = 4.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                    utbetalinger = listOf(utbetaling),
                    avstemmingXmlRequest = "<Root></Root>"
                )
            )

            val hentet = repo.hentSisteAvstemming()!!
            hentet shouldBe second
        }
    }

    @Test
    fun `hent utbetalinger for avstemming`() {
        withMigratedDb {
            val oppdragsmeldingTidspunkt = 11.oktober(2020).atTime(15, 30, 0).toInstant(ZoneOffset.UTC)

            val sak = testDataHelper.insertSak(FnrGenerator.random())
            val utbetaling1 = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())

            testDataHelper.insertOppdragsmelding(
                utbetalingId = utbetaling1.id,
                oppdragsmelding = Oppdragsmelding(
                    status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                    originalMelding = "",
                    tidspunkt = oppdragsmeldingTidspunkt
                )
            )

            val utbetaling2 = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            testDataHelper.insertOppdragsmelding(
                utbetalingId = utbetaling2.id,
                oppdragsmelding = Oppdragsmelding(
                    status = Oppdragsmelding.Oppdragsmeldingstatus.FEIL,
                    originalMelding = "",
                    tidspunkt = oppdragsmeldingTidspunkt
                )
            )

            repo.hentUtbetalingerForAvstemming(
                fom = 10.oktober(2020).startOfDay(),
                tom = 10.oktober(2020).endOfDay()
            ) shouldBe emptyList()

            repo.hentUtbetalingerForAvstemming(
                fom = 11.oktober(2020).startOfDay(),
                tom = 11.oktober(2020).endOfDay()
            ) shouldHaveSize 1

            repo.hentUtbetalingerForAvstemming(
                fom = 12.oktober(2020).startOfDay(),
                tom = 12.oktober(2020).endOfDay()
            ) shouldBe emptyList()
        }
    }

    @Test
    fun `hent utbetalinger for avstemming tidspunkt test`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling1 = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            testDataHelper.insertOppdragsmelding(
                utbetalingId = utbetaling1.id,
                oppdragsmelding = Oppdragsmelding(
                    status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                    originalMelding = "",
                    tidspunkt = 11.oktober(2020).startOfDay()
                )
            )

            val utbetaling2 = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            testDataHelper.insertOppdragsmelding(
                utbetalingId = utbetaling2.id,
                oppdragsmelding = Oppdragsmelding(
                    status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                    originalMelding = "",
                    tidspunkt = 11.oktober(2020).endOfDay()
                )
            )

            val utbetaling3 = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            testDataHelper.insertOppdragsmelding(
                utbetalingId = utbetaling3.id,
                oppdragsmelding = Oppdragsmelding(
                    status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                    originalMelding = "",
                    tidspunkt = 12.oktober(2020).startOfDay()
                )
            )

            val utbetalinger = repo.hentUtbetalingerForAvstemming(
                11.oktober(2020).startOfDay(),
                11.oktober(2020).endOfDay()
            )

            utbetalinger shouldHaveSize 2
            utbetalinger.map { it.id } shouldContainAll listOf(utbetaling1.id, utbetaling2.id)
        }
    }

    @Test
    fun `oppretter avstemming og oppdaterer aktuelle utbetalinger`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())

            testDataHelper.insertOppdragsmelding(
                utbetalingId = utbetaling.id,
                oppdragsmelding = Oppdragsmelding(
                    status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                    originalMelding = ""
                )
            )

            val avstemming = Avstemming(
                id = UUID30.randomUUID(),
                opprettet = now(),
                fom = now(),
                tom = now(),
                utbetalinger = listOf(utbetaling),
                avstemmingXmlRequest = "some xml"
            )

            repo.opprettAvstemming(avstemming)
            repo.oppdaterAvstemteUtbetalinger(avstemming)

            val oppdatertUtbetaling = testDataHelper.hentUtbetaling(utbetaling.id)
            oppdatertUtbetaling!!.getAvstemmingId() shouldBe avstemming.id
        }
    }

    private fun defaultUtbetaling() = Utbetaling(
        id = UUID30.randomUUID(),
        utbetalingslinjer = listOf(),
        fnr = FNR
    )
}
