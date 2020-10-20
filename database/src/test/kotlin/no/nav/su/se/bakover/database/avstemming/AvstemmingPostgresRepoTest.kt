package no.nav.su.se.bakover.database.avstemming

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.antall
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test

internal class AvstemmingPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = AvstemmingPostgresRepo(EmbeddedDatabase.instance())
    private val utbetalingRepo = UtbetalingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `henter siste avstemming`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FnrGenerator.random())
            val utbetaling = oversendtUtbetaling(oppdragId = sak.oppdrag.id)
            utbetalingRepo.opprettUtbetaling(utbetaling)
                .also {
                    utbetalingRepo.oppdaterMedKvittering(
                        utbetaling.id,
                        Kvittering(
                            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                            originalKvittering = "hallo",
                            mottattTidspunkt = now()
                        )
                    )
                }

            val zero = repo.hentSisteAvstemming()
            zero shouldBe null

            repo.opprettAvstemming(
                Avstemming(
                    fraOgMed = 1.januar(2020).startOfDay(),
                    tilOgMed = 2.januar(2020).startOfDay(),
                    utbetalinger = listOf(utbetaling)
                )
            )

            val second = repo.opprettAvstemming(
                Avstemming(
                    fraOgMed = 3.januar(2020).startOfDay(),
                    tilOgMed = 4.januar(2020).startOfDay(),
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
            val sak = testDataHelper.insertSak(FnrGenerator.random())
            val utbetaling = oversendtUtbetaling(
                oppdragId = sak.oppdrag.id,
                avstemmingsnøkkel = Avstemmingsnøkkel(11.oktober(2020).atStartOfDay().toTidspunkt())
            )
            utbetalingRepo.opprettUtbetaling(
                utbetaling
            )

            EmbeddedDatabase.instance().withSession { session ->
                """
                    insert into utbetaling (id, opprettet, oppdragId, fnr, type, behandler, avstemmingsnøkkel, simulering, utbetalingsrequest)
                    values (:id, :opprettet, :oppdragId, :fnr, :type, :behandler, to_json(:avstemmingsnokkel::json), to_json(:simulering::json), to_json(:utbetalingsrequest::json))
                 """.oppdatering(
                    mapOf(
                        "id" to UUID30.randomUUID(),
                        "opprettet" to Tidspunkt.now(),
                        "oppdragId" to sak.oppdrag.id,
                        "fnr" to FNR,
                        "type" to "NY",
                        "behandler" to "Z123",
                        "avstemmingsnokkel" to objectMapper.writeValueAsString(
                            Avstemmingsnøkkel(
                                9.oktober(2020).atStartOfDay().toTidspunkt()
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
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling1 = oversendtUtbetaling(
                oppdragId = sak.oppdrag.id,
                avstemmingsnøkkel = Avstemmingsnøkkel(11.oktober(2020).startOfDay())
            )
            utbetalingRepo.opprettUtbetaling(utbetaling1)

            val utbetaling2 = oversendtUtbetaling(
                oppdragId = sak.oppdrag.id,
                avstemmingsnøkkel = Avstemmingsnøkkel(11.oktober(2020).endOfDay())
            )
            utbetalingRepo.opprettUtbetaling(utbetaling2)

            utbetalingRepo.opprettUtbetaling(
                oversendtUtbetaling(
                    oppdragId = sak.oppdrag.id,
                    avstemmingsnøkkel = Avstemmingsnøkkel(12.oktober(2020).startOfDay())
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
            val utbetaling = oversendtUtbetaling(
                oppdragId = sak.oppdrag.id
            )
            utbetalingRepo.opprettUtbetaling(utbetaling)

            utbetalingRepo.oppdaterMedKvittering(
                utbetaling.id,
                Kvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                    originalKvittering = "",
                    mottattTidspunkt = Tidspunkt.now()
                )
            )

            val avstemming = Avstemming(
                id = UUID30.randomUUID(),
                opprettet = now(),
                fraOgMed = now(),
                tilOgMed = now(),
                utbetalinger = listOf(utbetaling),
                avstemmingXmlRequest = "some xml"
            )

            repo.opprettAvstemming(avstemming)
            repo.oppdaterAvstemteUtbetalinger(avstemming)

            EmbeddedDatabase.instance().withSession { session ->
                "select count(*) from Utbetaling where avstemmingId is not null".antall(session = session)
            } shouldBe 1
        }
    }

    private fun oversendtUtbetaling(
        oppdragId: UUID30,
        avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel()
    ) = Utbetaling.OversendtUtbetaling.UtenKvittering(
        id = UUID30.randomUUID(),
        utbetalingslinjer = listOf(),
        fnr = FNR,
        simulering = Simulering(
            gjelderId = FNR,
            gjelderNavn = "",
            datoBeregnet = idag(),
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        utbetalingsrequest = Utbetalingsrequest(""),
        type = Utbetaling.UtbetalingsType.NY,
        oppdragId = oppdragId,
        behandler = NavIdentBruker.Attestant("Z123"),
        avstemmingsnøkkel = avstemmingsnøkkel
    )
}
