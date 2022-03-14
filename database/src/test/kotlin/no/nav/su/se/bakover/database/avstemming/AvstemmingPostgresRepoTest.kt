package no.nav.su.se.bakover.database.avstemming

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.antall
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class AvstemmingPostgresRepoTest {

    @Test
    fun `henter siste grensesnittsavstemming`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo

            val zero = repo.hentSisteGrensesnittsavstemming()
            zero shouldBe null
            val utbetaling1 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().third

            repo.opprettGrensesnittsavstemming(
                Avstemming.Grensesnittavstemming(
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2020).startOfDay(),
                    tilOgMed = 2.januar(2020).startOfDay(),
                    utbetalinger = listOf(utbetaling1),
                ),
            )
            val utbetaling2 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().third
            val second = Avstemming.Grensesnittavstemming(
                opprettet = fixedTidspunkt,
                fraOgMed = 3.januar(2020).startOfDay(),
                tilOgMed = 4.januar(2020).startOfDay(),
                utbetalinger = listOf(utbetaling2),
                avstemmingXmlRequest = "<Root></Root>",
            )

            repo.opprettGrensesnittsavstemming(second)

            repo.hentSisteGrensesnittsavstemming()!! shouldBe second
        }
    }

    @Test
    fun `hent utbetalinger for grensesnittsavstemming`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val (_, vedtak, utbetaling) =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                    avstemmingsnøkkel = Avstemmingsnøkkel(
                        11.oktober(
                            2020,
                        ).startOfDay(),
                    ),
                )

            dataSource.withSession { session ->
                """
                    insert into utbetaling (id, opprettet, sakId, fnr, type, behandler, avstemmingsnøkkel, simulering, utbetalingsrequest)
                    values (:id, :opprettet, :sakId, :fnr, :type, :behandler, to_json(:avstemmingsnokkel::json), to_json(:simulering::json), to_json(:utbetalingsrequest::json))
                """.insert(
                    mapOf(
                        "id" to UUID30.randomUUID(),
                        "opprettet" to fixedTidspunkt,
                        "sakId" to vedtak.behandling.sakId,
                        "fnr" to vedtak.behandling.fnr,
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
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val (_, _, utbetaling1) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                avstemmingsnøkkel = Avstemmingsnøkkel(
                    11.oktober(2020).startOfDay(),
                ),
            )
            val (_, _, utbetaling2) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                avstemmingsnøkkel = Avstemmingsnøkkel(
                    11.oktober(2020).endOfDay(),
                ),
            )
            val utbetalinger = repo.hentUtbetalingerForGrensesnittsavstemming(
                11.oktober(2020).startOfDay(),
                11.oktober(2020).endOfDay(),
            )
            utbetalinger shouldHaveSize 2
            utbetalinger.map { it.id } shouldContainAll listOf(utbetaling1.id, utbetaling2.id)
        }
    }

    @Test
    fun `oppretter grensesnittsavstemming og oppdaterer aktuelle utbetalinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().third

            val avstemming = Avstemming.Grensesnittavstemming(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                fraOgMed = fixedTidspunkt,
                tilOgMed = fixedTidspunkt,
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
            )

            repo.opprettGrensesnittsavstemming(avstemming)
            repo.oppdaterUtbetalingerEtterGrensesnittsavstemming(avstemming)

            dataSource.withSession { session ->
                "select count(*) from Utbetaling where avstemmingId is not null".antall(session = session)
            } shouldBe 1
        }
    }

    @Test
    fun `oppretter og henter konsistensavstemming`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().third

            val avstemming = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                løpendeFraOgMed = 1.januar(2020).startOfDay(zoneIdOslo),
                opprettetTilOgMed = 31.januar(2021).endOfDay(zoneIdOslo),
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
            )

            repo.opprettKonsistensavstemming(avstemming)

            repo.hentKonsistensavstemming(avstemming.id) shouldBe Avstemming.Konsistensavstemming.Fullført(
                id = avstemming.id,
                opprettet = avstemming.opprettet,
                løpendeFraOgMed = avstemming.løpendeFraOgMed,
                opprettetTilOgMed = avstemming.opprettetTilOgMed,
                utbetalinger = mapOf(
                    oversendtUtbetalingMedKvittering.saksnummer to oversendtUtbetalingMedKvittering.utbetalingslinjer,
                ),
                avstemmingXmlRequest = "some xml",
            )
        }
    }

    @Test
    fun `finner ut om konsistensavstemming er utført for og på aktuell dato`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().third

            val avstemming1 = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = 1.januar(2021).startOfDay(ZoneOffset.UTC),
                løpendeFraOgMed = 1.januar(2021).startOfDay(zoneIdOslo),
                opprettetTilOgMed = 31.desember(2020).endOfDay(zoneIdOslo),
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
            )

            repo.opprettKonsistensavstemming(avstemming1)

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.januar(2021),
            ) shouldBe true

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 31.desember(2020),
            ) shouldBe false

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 2.januar(2021),
            ) shouldBe false

            val avstemming2 = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = 1.februar(2021).startOfDay(ZoneOffset.UTC),
                løpendeFraOgMed = 1.april(2021).startOfDay(zoneIdOslo),
                opprettetTilOgMed = 31.mars(2021).endOfDay(zoneIdOslo),
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
            )

            repo.opprettKonsistensavstemming(avstemming2)

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.februar(2021),
            ) shouldBe false
            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.april(2021),
            ) shouldBe false

            val avstemming3 = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = 1.mars(2021).startOfDay(ZoneOffset.UTC),
                løpendeFraOgMed = 1.juni(2021).startOfDay(zoneIdOslo),
                opprettetTilOgMed = 31.mai(2021).endOfDay(zoneIdOslo),
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
            )

            repo.opprettKonsistensavstemming(avstemming3)

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.mars(2021),
            ) shouldBe false
            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.juni(2021),
            ) shouldBe false
        }
    }

    @Test
    fun `konsistensavstemming henter kun utbetalinger hvor det eksisterer utbetalingslinjer med tom større enn eller lik løpendeFraOgMed`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().third

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
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().third

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
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val avstemming = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
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

    @Test
    fun `konsistensavstemming henter hele utbetalingen selv om bare en linje er løpende etter løpendeFraOgMed`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val første = Utbetalingslinje.Ny(
                opprettet = fixedTidspunkt,
                fraOgMed = 1.januar(2020),
                tilOgMed = 30.april(2020),
                forrigeUtbetalingslinjeId = null,
                beløp = 15000,
                uføregrad = Uføregrad.parse(50),
            )
            val andre = Utbetalingslinje.Ny(
                opprettet = fixedTidspunkt,
                fraOgMed = 1.mai(2020),
                tilOgMed = 31.desember(2020),
                forrigeUtbetalingslinjeId = første.id,
                beløp = 17000,
                uføregrad = Uføregrad.parse(40),
            )
            val oversendtUtbetalingMedKvittering = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                utbetalingslinjer = nonEmptyListOf(første, andre),
            ).second

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = 1.januar(2020).startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
            )[0].utbetalingslinjer shouldBe nonEmptyListOf(første, andre)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = 1.mai(2020).startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
            )[0].utbetalingslinjer shouldBe nonEmptyListOf(første, andre)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = 1.desember(2020).startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
            )[0].utbetalingslinjer shouldBe nonEmptyListOf(første, andre)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = 1.januar(2021).startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
            ) shouldBe emptyList()
        }
    }
}
