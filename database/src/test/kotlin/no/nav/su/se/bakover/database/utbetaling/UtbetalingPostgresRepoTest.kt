package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import økonomi.domain.utbetaling.Utbetaling
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
                repo.hentOversendtUtbetalingForAvstemmingsnøkkel(utbetalingMedKvittering.avstemmingsnøkkel) as Utbetaling.OversendtUtbetaling.MedKvittering
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

            val utbetalingUtenKvittering: Utbetaling.OversendtUtbetaling.UtenKvittering =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilget(kvittering = null).fourth as Utbetaling.OversendtUtbetaling.UtenKvittering

            repo.hentUkvitterteUtbetalinger().map { it.id } shouldBe listOf(
                utbetalingUtenKvittering.id,
            )
        }
    }

    @Test
    fun `hent utbetalinger for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val clock = testDataHelper.clock
            val repo = testDataHelper.utbetalingRepo
            val sakId = UUID.randomUUID()
            val fnr = Fnr.generer()
            // Lagrer en kvittering med og uten kvittering som ikke skal komme med i hent-operasjonen
            testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            testDataHelper.persisterSøknadsbehandlingIverksatt(kvittering = null)

            val (_, _, utbetalingMedKvittering1: Utbetaling.OversendtUtbetaling.MedKvittering) =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
                    sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(sakId = sakId, fnr = fnr),
                    søknadsbehandling = { (sak, søknad) ->
                        iverksattSøknadsbehandlingUføre(
                            sakInfo = sak.info(),
                            stønadsperiode = Stønadsperiode.create(år(2021)),
                            sakOgSøknad = sak to søknad,
                            clock = clock,
                        )
                    },
                )

            val utbetalingMedKvittering2: Utbetaling.OversendtUtbetaling.MedKvittering =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
                    sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(sakId = sakId, fnr = fnr),
                    søknadsbehandling = { (sak, søknad) ->
                        iverksattSøknadsbehandlingUføre(
                            sakInfo = sak.info(),
                            stønadsperiode = Stønadsperiode.create(år(2022)),
                            sakOgSøknad = sak to søknad,
                            clock = clock,
                        )
                    },
                ).third

            val utbetalingUtenKvittering: Utbetaling.OversendtUtbetaling.UtenKvittering =
                testDataHelper.persisterSøknadsbehandlingIverksatt(
                    sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(sakId = sakId, fnr = fnr),
                    søknadsbehandling = { (sak, søknad) ->
                        iverksattSøknadsbehandlingUføre(
                            sakInfo = sak.info(),
                            stønadsperiode = Stønadsperiode.create(år(2023)),
                            sakOgSøknad = sak to søknad,
                            clock = clock,
                            kvittering = null,
                        )
                    },
                    kvittering = null,
                ).fourth as Utbetaling.OversendtUtbetaling.UtenKvittering

            repo.hentOversendteUtbetalinger(sakId).sortedBy { it.avstemmingsnøkkel } shouldBe listOf(
                utbetalingMedKvittering1,
                utbetalingMedKvittering2,
                utbetalingUtenKvittering,
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
            repo.hentOversendtUtbetalingForUtbetalingId(utbetalingMedKvittering.id, null) shouldBe utbetalingMedKvittering
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
