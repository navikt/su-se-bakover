package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.NonEmptyList
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.idag
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.domain.oppdrag.ForrigeUtbetalingslinjeKoblendeListe
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertMåned
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import org.junit.jupiter.api.Test
import økonomi.domain.kvittering.Kvittering
import java.time.LocalDate
import java.util.UUID

internal class GrensesnittavstemmingDataBuilderTest {

    @Test
    fun `Sjekk at vi bygger AvstemmingDataReqest riktig`() {
        val avstemmingId = UUID30.randomUUID()
        val expected = GrensesnittsavstemmingData(
            aksjon = Aksjonsdata.Grensesnittsavstemming(
                nokkelFom = "1583017200000000000",
                nokkelTom = "1583103600000000000",
                avleverendeAvstemmingId = avstemmingId.toString(),
                underkomponentKode = "SUUFORE",
            ),
            total = Totaldata(
                totalAntall = 5,
                totalBelop = 18000.toBigDecimal(),
                fortegn = Fortegn.TILLEGG,
            ),
            periode = Periodedata(
                datoAvstemtFom = "2020030100",
                datoAvstemtTom = "2020030200",
            ),
            grunnlag = GrensesnittsavstemmingData.Grunnlagdata(
                godkjentAntall = 2,
                godkjentBelop = 1600.toBigDecimal(),
                godkjentFortegn = Fortegn.TILLEGG,
                varselAntall = 1,
                varselBelop = 1400.toBigDecimal(),
                varselFortegn = Fortegn.TILLEGG,
                avvistAntall = 1,
                avvistBelop = 10000.toBigDecimal(),
                avvistFortegn = Fortegn.TILLEGG,
                manglerAntall = 1,
                manglerBelop = 5000.toBigDecimal(),
                manglerFortegn = Fortegn.TILLEGG,
            ),
            detalj = listOf(
                GrensesnittsavstemmingData.Detaljdata(
                    detaljType = GrensesnittsavstemmingData.Detaljdata.Detaljtype.GODKJENT_MED_VARSEL,
                    offnr = "12345678910",
                    avleverendeTransaksjonNokkel = okMedVarselId.toString(),
                    tidspunkt = "2020-03-02-00.00.00.000000",
                ),
                GrensesnittsavstemmingData.Detaljdata(
                    detaljType = GrensesnittsavstemmingData.Detaljdata.Detaljtype.AVVIST,
                    offnr = "12345678910",
                    avleverendeTransaksjonNokkel = feildId.toString(),
                    tidspunkt = "2020-03-01-00.00.00.000000",
                ),
                GrensesnittsavstemmingData.Detaljdata(
                    detaljType = GrensesnittsavstemmingData.Detaljdata.Detaljtype.MANGLENDE_KVITTERING,
                    offnr = "12345678910",
                    avleverendeTransaksjonNokkel = manglerKvitteringId.toString(),
                    tidspunkt = "2020-03-02-00.00.00.000000",
                ),
            ),
        )

        GrensesnittavstemmingDataBuilder(
            Avstemming.Grensesnittavstemming(
                id = avstemmingId,
                opprettet = fixedTidspunkt,
                fraOgMed = 1.mars(2020).atStartOfDay(zoneIdOslo).toTidspunkt(),
                tilOgMed = 2.mars(2020).atStartOfDay(zoneIdOslo).toTidspunkt(),
                utbetalinger = alleUtbetalinger(),
                avstemmingXmlRequest = null,
                fagområde = Fagområde.SUUFORE,
            ),
        ).build() shouldBe expected
    }
}

internal val saksnummer = Saksnummer(2021)
internal val sakId = UUID.randomUUID()

internal fun lagUtbetaling(
    id: UUID30 = UUID30.randomUUID(),
    opprettet: LocalDate,
    status: Kvittering.Utbetalingsstatus?,
    linjer: NonEmptyList<Utbetalingslinje>,
    oppdragsmelding: Utbetalingsrequest = Utbetalingsrequest(
        value = "Melding",
    ),
): Utbetaling.OversendtUtbetaling = lagUtbetaling(
    id = id,
    opprettet = opprettet.atStartOfDay(zoneIdOslo).toTidspunkt(),
    status = status,
    linjer = linjer,
    oppdragsmelding = oppdragsmelding,
)

internal fun lagUtbetaling(
    id: UUID30 = UUID30.randomUUID(),
    opprettet: Tidspunkt,
    status: Kvittering.Utbetalingsstatus?,
    linjer: NonEmptyList<Utbetalingslinje>,
    oppdragsmelding: Utbetalingsrequest = Utbetalingsrequest(
        value = "Melding",
    ),
): Utbetaling.OversendtUtbetaling = when (status) {
    null -> {
        Utbetaling.UtbetalingForSimulering(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = linjer,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = simulering,
        ).toOversendtUtbetaling(
            oppdragsmelding = oppdragsmelding,
        )
    }

    else -> {
        Utbetaling.UtbetalingForSimulering(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = linjer,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = simulering,
        ).toOversendtUtbetaling(
            oppdragsmelding = oppdragsmelding,
        ).toKvittertUtbetaling(
            kvittering = Kvittering(
                utbetalingsstatus = status,
                originalKvittering = "hallo",
                mottattTidspunkt = fixedTidspunkt,
            ),
        )
    }
}

internal val fnr = Fnr("12345678910")
internal val ok1Id = UUID30.randomUUID()
internal val ok2Id = UUID30.randomUUID()
internal val okMedVarselId = UUID30.randomUUID()
internal val feildId = UUID30.randomUUID()
internal val manglerKvitteringId = UUID30.randomUUID()
private val simulering = Simulering(
    gjelderId = fnr,
    gjelderNavn = "",
    datoBeregnet = idag(fixedClock),
    nettoBeløp = 0,
    måneder = SimulertMåned.create(år(2021)),
    rawResponse = "GrensesnittavstemmingDataBuilderTest baserer seg ikke på rå XML.",
)

internal fun alleUtbetalinger(): List<Utbetaling.OversendtUtbetaling> {
    val clock = TikkendeKlokke(fixedClockAt(1.mars(2020)))

    val førsteUtbetalingFørsteUtbetalingslinjeId = UUID30.randomUUID()
    val andreUtbetalingFørsteUtbetalingslinjeId = UUID30.randomUUID()
    return listOf(
        lagUtbetaling(
            id = ok1Id,
            opprettet = clock.nextTidspunkt(),
            status = Kvittering.Utbetalingsstatus.OK,
            linjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    utbetalingslinjeNy(
                        periode = mars(2020),
                        beløp = 100,
                        opprettet = clock.nextTidspunkt(),
                        id = førsteUtbetalingFørsteUtbetalingslinjeId,
                    ),
                    utbetalingslinjeNy(
                        periode = april(2020),
                        beløp = 200,
                        opprettet = clock.nextTidspunkt(),
                        rekkefølge = Rekkefølge.skip(0),
                        forrigeUtbetalingslinjeId = førsteUtbetalingFørsteUtbetalingslinjeId,
                    ),
                ),
            ).toNonEmptyList(),
        ),
        lagUtbetaling(
            id = ok2Id,
            opprettet = clock.nextTidspunkt(),
            status = Kvittering.Utbetalingsstatus.OK,
            linjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    utbetalingslinjeNy(
                        periode = mars(2020),
                        beløp = 600,
                        uføregrad = 60,
                        opprettet = clock.nextTidspunkt(),
                        id = andreUtbetalingFørsteUtbetalingslinjeId,
                    ),
                    utbetalingslinjeNy(
                        periode = april(2020),
                        beløp = 700,
                        uføregrad = 60,
                        opprettet = clock.nextTidspunkt(),
                        rekkefølge = Rekkefølge.skip(0),
                    ),
                ),
            ).toNonEmptyList(),
        ),
        lagUtbetaling(
            id = okMedVarselId,
            opprettet = 2.mars(2020),
            status = Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
            linjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    utbetalingslinjeNy(
                        periode = mars(2020),
                        beløp = 400,
                        uføregrad = 70,
                        opprettet = clock.nextTidspunkt(),
                    ),
                    utbetalingslinjeNy(
                        periode = april(2020),
                        beløp = 500,
                        uføregrad = 70,
                        opprettet = clock.nextTidspunkt(),
                        rekkefølge = Rekkefølge.skip(0),
                    ),
                    utbetalingslinjeNy(
                        periode = mai(2020),
                        beløp = 500,
                        uføregrad = 75,
                        opprettet = clock.nextTidspunkt(),
                        rekkefølge = Rekkefølge.skip(1),
                    ),
                ),
            ).toNonEmptyList(),
        ),
        lagUtbetaling(
            id = feildId,
            opprettet = 1.mars(2020),
            status = Kvittering.Utbetalingsstatus.FEIL,
            linjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    utbetalingslinjeNy(
                        periode = mars(2020),
                        beløp = 1000,
                        uføregrad = 10,
                        opprettet = clock.nextTidspunkt(),
                    ),
                    utbetalingslinjeNy(
                        periode = april(2020),
                        beløp = 2000,
                        uføregrad = 20,
                        opprettet = clock.nextTidspunkt(),
                        rekkefølge = Rekkefølge.skip(0),
                    ),
                    utbetalingslinjeNy(
                        periode = mai(2020),
                        beløp = 3000,
                        uføregrad = 30,
                        opprettet = clock.nextTidspunkt(),
                        rekkefølge = Rekkefølge.skip(1),
                    ),
                    utbetalingslinjeNy(
                        periode = juni(2020),
                        beløp = 4000,
                        uføregrad = 50,
                        opprettet = clock.nextTidspunkt(),
                        rekkefølge = Rekkefølge.skip(2),
                    ),
                ),
            ).toNonEmptyList(),
        ),
        lagUtbetaling(
            id = manglerKvitteringId,
            opprettet = 2.mars(2020),
            status = null,
            linjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    utbetalingslinjeNy(
                        periode = år(2020),
                        beløp = 5000,
                        uføregrad = 15,
                        opprettet = clock.nextTidspunkt(),
                    ),
                ),
            ).toNonEmptyList(),
        ),
    )
}
