package no.nav.su.se.bakover.database.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.utbetalingslinje
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtbetalingPostgresRepoTest {

    @Test
    fun `hent utbetaling fra avstemmingsnøkkel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val utbetalingMedKvittering =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().third
            val hentet =
                repo.hentUtbetaling(utbetalingMedKvittering.avstemmingsnøkkel) as Utbetaling.OversendtUtbetaling.MedKvittering
            utbetalingMedKvittering shouldBe hentet
        }
    }

    @Test
    fun `hent utbetalinger uten kvittering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val sakId = UUID.randomUUID()
            // Lagrer en med kvittering som ikke skal komme med i hent-operasjonen
            testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                sakId = sakId,
                stønadsperiode = Stønadsperiode.create(
                    januar(2021),
                ),
            )
            val utbetalingUtenKvittering1 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingUtenKvittering(
                    sakId = sakId,
                    stønadsperiode = Stønadsperiode.create(
                        februar(2021),
                    ),

                ).second
            val utbetalingUtenKvittering2 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingUtenKvittering(
                    sakId = sakId,
                    stønadsperiode = Stønadsperiode.create(
                        mars(2021),
                    ),
                ).second
            repo.hentUkvitterteUtbetalinger() shouldBe listOf(utbetalingUtenKvittering1, utbetalingUtenKvittering2)
        }
    }

    @Test
    fun `hent utbetalinger for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val sakId = UUID.randomUUID()
            // Lagrer en kvittering med og uten kvittering som ikke skal komme med i hent-operasjonen
            testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering()
            testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering()

            val utbetalingUtenKvittering1 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                    sakId = sakId,
                    stønadsperiode = Stønadsperiode.create(januar(2021)),
                ).third
            val utbetalingUtenKvittering2 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                    sakId = sakId,
                    stønadsperiode = Stønadsperiode.create(februar(2021)),
                ).third
            val utbetalingMedKvittering1 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                    sakId = sakId,
                    stønadsperiode = Stønadsperiode.create(mars(2021)),
                ).third
            val utbetalingMedKvittering2 =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                    sakId = sakId,
                    stønadsperiode = Stønadsperiode.create(april(2021)),
                ).third
            repo.hentUtbetalinger(sakId).sortedBy { it.avstemmingsnøkkel } shouldBe listOf(
                utbetalingUtenKvittering1,
                utbetalingUtenKvittering2,
                utbetalingMedKvittering1,
                utbetalingMedKvittering2,
            ).sortedBy { it.avstemmingsnøkkel }
        }
    }

    @Test
    fun `oppdater med kvittering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val utbetalingMedKvittering: Utbetaling.OversendtUtbetaling.MedKvittering =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering().third
            repo.hentUtbetaling(utbetalingMedKvittering.id) shouldBe utbetalingMedKvittering
        }
    }

    @Test
    fun `Lagrer og henter med kjøreplan`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val utbetalingMedKvittering: Utbetaling.OversendtUtbetaling.MedKvittering =
                testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                    utbetalingslinjer = nonEmptyListOf(
                        utbetalingslinje(
                            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling,
                        )
                    )
                ).third
            repo.hentUtbetaling(utbetalingMedKvittering.id) shouldBe utbetalingMedKvittering
        }
    }
}
