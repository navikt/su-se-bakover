package no.nav.su.se.bakover.database.avstemming

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
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
import java.time.temporal.ChronoUnit

internal class AvstemmingPostgresRepoTest {

    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = AvstemmingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `henter siste grensesnittsavstemming`() {
        withMigratedDb {
            val utbetalingMedKvittering =
                testDataHelper.nyOversendtUtbetalingMedKvittering()

            val zero = repo.hentSisteGrensesnittsavstemming()
            zero shouldBe null

            repo.opprettGrensesnittsavstemming(
                Avstemming.Grensesnittavstemming(
                    fraOgMed = 1.januar(2020).startOfDay(),
                    tilOgMed = 2.januar(2020).startOfDay(),
                    utbetalinger = listOf(utbetalingMedKvittering.second),
                ),
            )

            val second = Avstemming.Grensesnittavstemming(
                fraOgMed = 3.januar(2020).startOfDay(),
                tilOgMed = 4.januar(2020).startOfDay(),
                utbetalinger = listOf(utbetalingMedKvittering.second),
                avstemmingXmlRequest = "<Root></Root>",
            )

            repo.opprettGrensesnittsavstemming(second)

            repo.hentSisteGrensesnittsavstemming()!! shouldBe second
        }
    }

    @Test
    fun `hent utbetalinger for grensesnittsavstemming`() {
        withMigratedDb {
            val (innvilget, utbetaling) =
                testDataHelper.nyIverksattInnvilget(
                    avstemmingsnøkkel = Avstemmingsnøkkel(
                        11.oktober(
                            2020,
                        ).startOfDay(),
                    ),
                )

            testDataHelper.datasource.withSession { session ->
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
                                9.oktober(2020).startOfDay(),
                            ),
                        ),
                        "simulering" to "{}",
                        "utbetalingsrequest" to "{}",
                    ),
                    session,
                )
            }

            repo.hentUtbetalingerForGrensesnittsavstemming(
                fraOgMed = 10.oktober(2020).startOfDay(),
                tilOgMed = 10.oktober(2020).endOfDay(),
            ) shouldBe emptyList()

            repo.hentUtbetalingerForGrensesnittsavstemming(
                fraOgMed = 11.oktober(2020).startOfDay(),
                tilOgMed = 11.oktober(2020).endOfDay(),
            ) shouldBe listOf(utbetaling)

            repo.hentUtbetalingerForGrensesnittsavstemming(
                fraOgMed = 12.oktober(2020).startOfDay(),
                tilOgMed = 12.oktober(2020).endOfDay(),
            ) shouldBe emptyList()
        }
    }

    @Test
    fun `hent utbetalinger for grensesnittsavstemming tidspunkt test`() {
        withMigratedDb {
            val utbetaling1 =
                testDataHelper.nyOversendtUtbetalingMedKvittering(
                    avstemmingsnøkkel = Avstemmingsnøkkel(
                        11.oktober(2020).startOfDay(),
                    ),
                )

            val utbetaling2 =
                testDataHelper.nyOversendtUtbetalingMedKvittering(
                    avstemmingsnøkkel = Avstemmingsnøkkel(
                        11.oktober(2020).endOfDay(),
                    ),
                )

            val utbetalinger = repo.hentUtbetalingerForGrensesnittsavstemming(
                11.oktober(2020).startOfDay(),
                11.oktober(2020).endOfDay(),
            )

            utbetalinger shouldHaveSize 2
            utbetalinger.map { it.id } shouldContainAll listOf(utbetaling1.second.id, utbetaling2.second.id)
        }
    }

    @Test
    fun `oppretter grensesnittsavstemming og oppdaterer aktuelle utbetalinger`() {
        withMigratedDb {
            val oversendtUtbetalingMedKvittering =
                testDataHelper.nyOversendtUtbetalingMedKvittering()

            val avstemming = Avstemming.Grensesnittavstemming(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                fraOgMed = fixedTidspunkt,
                tilOgMed = fixedTidspunkt,
                utbetalinger = listOf(oversendtUtbetalingMedKvittering.second),
                avstemmingXmlRequest = "some xml",
            )

            repo.opprettGrensesnittsavstemming(avstemming)
            repo.oppdaterUtbetalingerEtterGrensesnittsavstemming(avstemming)

            testDataHelper.datasource.withSession { session ->
                "select count(*) from Utbetaling where avstemmingId is not null".antall(session = session)
            } shouldBe 1
        }
    }

    @Test
    fun `oppretter og henter konsistensavstemming`() {
        withMigratedDb {
            val oversendtUtbetalingMedKvittering =
                testDataHelper.nyOversendtUtbetalingMedKvittering()

            val avstemming = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                løpendeFraOgMed = 1.januar(2020).startOfDay(zoneIdOslo),
                opprettetTilOgMed = 31.januar(2021).endOfDay(zoneIdOslo),
                utbetalinger = listOf(oversendtUtbetalingMedKvittering.second),
                avstemmingXmlRequest = "some xml",
            )

            repo.opprettKonsistensavstemming(avstemming)

            repo.hentKonsistensavstemming(avstemming.id) shouldBe Avstemming.Konsistensavstemming.Fullført(
                id = avstemming.id,
                opprettet = avstemming.opprettet,
                løpendeFraOgMed = avstemming.løpendeFraOgMed,
                opprettetTilOgMed = avstemming.opprettetTilOgMed,
                utbetalinger = mapOf(
                    oversendtUtbetalingMedKvittering.first.saksnummer to oversendtUtbetalingMedKvittering.second.utbetalingslinjer,
                ),
                avstemmingXmlRequest = "some xml",
            )
        }
    }

    @Test
    fun `konsistensavstemming henter kun utbetalinger hvor det eksisterer utbetalingslinjer med tom større enn eller lik løpendeFraOgMed`() {
        withMigratedDb {
            val oversendtUtbetalingMedKvittering =
                testDataHelper.nyOversendtUtbetalingMedKvittering().second

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay()
                    .plus(1, ChronoUnit.DAYS),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay()
                    .minus(1, ChronoUnit.DAYS),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.senesteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)
        }
    }

    @Test
    fun `konsistensavstemming henter bare utbetalinger opprettet tidligere enn opprettetTilOgMed`() {
        withMigratedDb {
            val oversendtUtbetalingMedKvittering = testDataHelper.nyOversendtUtbetalingMedKvittering().second

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.minus(1, ChronoUnit.DAYS),
            ) shouldBe emptyList()

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)
        }
    }

    @Test
    fun `konsistensavstemming uten løpende utbetalinger`() {
        withMigratedDb {
            val avstemming = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.now(),
                løpendeFraOgMed = 1.januar(2021).startOfDay(),
                opprettetTilOgMed = 1.januar(2021).endOfDay(),
                utbetalinger = emptyList(),
                avstemmingXmlRequest = "xml",
            )

            repo.opprettKonsistensavstemming(avstemming)

            repo.hentKonsistensavstemming(avstemming.id) shouldBe Avstemming.Konsistensavstemming.Fullført(
                id = avstemming.id,
                opprettet = avstemming.opprettet,
                løpendeFraOgMed = 1.januar(2021).startOfDay(),
                opprettetTilOgMed = 1.januar(2021).endOfDay(),
                utbetalinger = emptyMap(),
                avstemmingXmlRequest = "xml",
            )
        }
    }
}
