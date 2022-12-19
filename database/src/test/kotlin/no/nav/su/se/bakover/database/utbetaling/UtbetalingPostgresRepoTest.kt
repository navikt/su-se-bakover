package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtbetalingPostgresRepoTest {

    @Test
    fun `hent utbetaling fra avstemmingsnøkkel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val utbetalingMedKvittering =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().third
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
            // Lagrer en med kvittering som ikke skal komme med i hent-operasjonen
            testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            val utbetalingUtenKvittering1 = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget().third.utbetalingId
            val utbetalingUtenKvittering2 = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget().third.utbetalingId

            repo.hentUkvitterteUtbetalinger().map { it.id } shouldBe listOf(utbetalingUtenKvittering1, utbetalingUtenKvittering2)
        }
    }

    @Test
    fun `hent utbetalinger for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val sakId = UUID.randomUUID()
            // Lagrer en kvittering med og uten kvittering som ikke skal komme med i hent-operasjonen
            testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()

            val utbetalingUtenKvittering1 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
                    sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(sakId),
                    søknadsbehandling = { (sak, søknad) ->
                        iverksattSøknadsbehandlingUføre(
                            sakInfo = sak.info(),
                            stønadsperiode = Stønadsperiode.create(år(2021)),
                            sakOgSøknad = sak to søknad,
                        )
                    },
                ).third
            val utbetalingUtenKvittering2 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
                    sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(sakId),
                    søknadsbehandling = { (sak, søknad) ->
                        iverksattSøknadsbehandlingUføre(
                            sakInfo = sak.info(),
                            stønadsperiode = Stønadsperiode.create(år(2022)),
                            sakOgSøknad = sak to søknad,
                        )
                    },
                ).third
            val utbetalingMedKvittering1 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
                    sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(sakId),
                    søknadsbehandling = { (sak, søknad) ->
                        iverksattSøknadsbehandlingUføre(
                            sakInfo = sak.info(),
                            stønadsperiode = Stønadsperiode.create(år(2023)),
                            sakOgSøknad = sak to søknad,
                        )
                    },
                ).third
            val utbetalingMedKvittering2 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
                    sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(sakId),
                    søknadsbehandling = { (sak, søknad) ->
                        iverksattSøknadsbehandlingUføre(
                            sakInfo = sak.info(),
                            stønadsperiode = Stønadsperiode.create(år(2024)),
                            sakOgSøknad = sak to søknad,
                        )
                    },
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
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().third
            repo.hentUtbetaling(utbetalingMedKvittering.id) shouldBe utbetalingMedKvittering
        }
    }

//    @Test
//    fun `Lagrer og henter med kjøreplan`() {
//        withMigratedDb { dataSource ->
//            val testDataHelper = TestDataHelper(dataSource)
//            val repo = testDataHelper.utbetalingRepo
//            val utbetalingMedKvittering: Utbetaling.OversendtUtbetaling.MedKvittering =
//                testDataHelper.persisterReguleringIverksatt(
//                    utbetalingslinjer = nonEmptyListOf(
//                        utbetalingslinje(
//                            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling,
//                        ),
//                    ),
//                ).third
//            repo.hentUtbetaling(utbetalingMedKvittering.id) shouldBe utbetalingMedKvittering
//        }
//    }
}
