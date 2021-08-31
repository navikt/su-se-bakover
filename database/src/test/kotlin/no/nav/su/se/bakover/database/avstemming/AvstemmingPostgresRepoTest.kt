package no.nav.su.se.bakover.database.avstemming

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.antall
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import org.junit.jupiter.api.Test

internal class AvstemmingPostgresRepoTest {

    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = AvstemmingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `henter siste avstemming`() {
        withMigratedDb {
            val utbetalingMedKvittering = testDataHelper.nyOversendtUtbetalingMedKvittering()

            val zero = repo.hentSisteAvstemming()
            zero shouldBe null

            repo.opprettAvstemming(
                Avstemming.Grensesnittavstemming(
                    fraOgMed = 1.januar(2020).startOfDay(),
                    tilOgMed = 2.januar(2020).startOfDay(),
                    utbetalinger = listOf(utbetalingMedKvittering.second)
                )
            )

            val second = repo.opprettAvstemming(
                Avstemming.Grensesnittavstemming(
                    fraOgMed = 3.januar(2020).startOfDay(),
                    tilOgMed = 4.januar(2020).startOfDay(),
                    utbetalinger = listOf(utbetalingMedKvittering.second),
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
            val (innvilget, utbetaling) = testDataHelper.nyIverksattInnvilget(
                avstemmingsnøkkel = Avstemmingsnøkkel(
                    11.oktober(
                        2020
                    ).startOfDay()
                )
            )

            EmbeddedDatabase.instance().withSession { session ->
                """
                    insert into utbetaling (id, opprettet, sakId, fnr, type, behandler, avstemmingsnøkkel, simulering, utbetalingsrequest)
                    values (:id, :opprettet, :sakId, :fnr, :type, :behandler, to_json(:avstemmingsnokkel::json), to_json(:simulering::json), to_json(:utbetalingsrequest::json))
                 """.insert(
                    mapOf(
                        "id" to UUID30.randomUUID(),
                        "opprettet" to fixedTidspunkt,
                        "sakId" to innvilget.sakId,
                        "fnr" to innvilget.fnr,
                        "type" to "NY",
                        "behandler" to "Z123",
                        "avstemmingsnokkel" to objectMapper.writeValueAsString(
                            Avstemmingsnøkkel(
                                9.oktober(2020).startOfDay()
                            )
                        ),
                        "simulering" to "{}",
                        "utbetalingsrequest" to "{}"
                    ),
                    session
                )
            }

            repo.hentUtbetalingerForAvstemming(
                fraOgMed = 10.oktober(2020).startOfDay(),
                tilOgMed = 10.oktober(2020).endOfDay()
            ) shouldBe emptyList()

            repo.hentUtbetalingerForAvstemming(
                fraOgMed = 11.oktober(2020).startOfDay(),
                tilOgMed = 11.oktober(2020).endOfDay()
            ) shouldBe listOf(utbetaling)

            repo.hentUtbetalingerForAvstemming(
                fraOgMed = 12.oktober(2020).startOfDay(),
                tilOgMed = 12.oktober(2020).endOfDay()
            ) shouldBe emptyList()
        }
    }

    @Test
    fun `hent utbetalinger for avstemming tidspunkt test`() {
        withMigratedDb {
            val utbetaling1 = testDataHelper.nyOversendtUtbetalingMedKvittering(
                avstemmingsnøkkel = Avstemmingsnøkkel(
                    11.oktober(2020).startOfDay()
                )
            )

            val utbetaling2 = testDataHelper.nyOversendtUtbetalingMedKvittering(
                avstemmingsnøkkel = Avstemmingsnøkkel(
                    11.oktober(2020).endOfDay()
                )
            )

            val utbetalinger = repo.hentUtbetalingerForAvstemming(
                11.oktober(2020).startOfDay(),
                11.oktober(2020).endOfDay()
            )

            utbetalinger shouldHaveSize 2
            utbetalinger.map { it.id } shouldContainAll listOf(utbetaling1.second.id, utbetaling2.second.id)
        }
    }

    @Test
    fun `oppretter avstemming og oppdaterer aktuelle utbetalinger`() {
        withMigratedDb {
            val oversendtUtbetalingMedKvittering = testDataHelper.nyOversendtUtbetalingMedKvittering()

            val avstemming = Avstemming.Grensesnittavstemming(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                fraOgMed = fixedTidspunkt,
                tilOgMed = fixedTidspunkt,
                utbetalinger = listOf(oversendtUtbetalingMedKvittering.second),
                avstemmingXmlRequest = "some xml"
            )

            repo.opprettAvstemming(avstemming)
            repo.oppdaterAvstemteUtbetalinger(avstemming)

            EmbeddedDatabase.instance().withSession { session ->
                "select count(*) from Utbetaling where avstemmingId is not null".antall(session = session)
            } shouldBe 1
        }
    }
}
