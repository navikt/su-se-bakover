package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.NonEmptyList
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.domain.oppdrag.ForrigeUtbetbetalingslinjeKoblendeListe
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetalingslinje
import org.junit.jupiter.api.Test
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
): Utbetaling.OversendtUtbetaling = when (status) {
    null -> {
        Utbetaling.UtbetalingForSimulering(
            id = id,
            opprettet = opprettet.atStartOfDay(zoneIdOslo).toTidspunkt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = linjer,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet.atStartOfDay(zoneIdOslo).toTidspunkt()),
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
            opprettet = opprettet.atStartOfDay(zoneIdOslo).toTidspunkt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = linjer,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet.atStartOfDay(zoneIdOslo).toTidspunkt()),
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
    periodeList = listOf(
        SimulertPeriode(
            fraOgMed = år(2021).fraOgMed,
            tilOgMed = år(2021).tilOgMed,
            utbetaling = null,
        ),
    ),
    rawResponse = "GrensesnittavstemmingDataBuilderTest baserer seg ikke på rå XML.",
)

internal fun alleUtbetalinger() = listOf(
    lagUtbetaling(
        id = ok1Id,
        opprettet = 1.mars(2020),
        status = Kvittering.Utbetalingsstatus.OK,
        linjer = ForrigeUtbetbetalingslinjeKoblendeListe(
            listOf(
                utbetalingslinje(periode = mars(2020), beløp = 100),
                utbetalingslinje(periode = april(2020), beløp = 200),
            ),
        ).toNonEmptyList(),
    ),
    lagUtbetaling(
        id = ok2Id,
        opprettet = 1.mars(2020),
        status = Kvittering.Utbetalingsstatus.OK,
        linjer = ForrigeUtbetbetalingslinjeKoblendeListe(
            listOf(
                utbetalingslinje(periode = mars(2020), beløp = 600, uføregrad = 60),
                utbetalingslinje(periode = april(2020), beløp = 700, uføregrad = 60),
            ),
        ).toNonEmptyList(),
    ),
    lagUtbetaling(
        id = okMedVarselId,
        opprettet = 2.mars(2020),
        status = Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
        linjer = ForrigeUtbetbetalingslinjeKoblendeListe(
            listOf(
                utbetalingslinje(periode = mars(2020), beløp = 400, uføregrad = 70),
                utbetalingslinje(periode = april(2020), beløp = 500, uføregrad = 70),
                utbetalingslinje(periode = mai(2020), beløp = 500, uføregrad = 75),
            ),
        ).toNonEmptyList(),
    ),
    lagUtbetaling(
        id = feildId,
        opprettet = 1.mars(2020),
        status = Kvittering.Utbetalingsstatus.FEIL,
        linjer = ForrigeUtbetbetalingslinjeKoblendeListe(
            listOf(
                utbetalingslinje(periode = mars(2020), beløp = 1000, uføregrad = 10),
                utbetalingslinje(periode = april(2020), beløp = 2000, uføregrad = 20),
                utbetalingslinje(periode = mai(2020), beløp = 3000, uføregrad = 30),
                utbetalingslinje(periode = juni(2020), beløp = 4000, uføregrad = 50),
            ),
        ).toNonEmptyList(),
    ),
    lagUtbetaling(
        id = manglerKvitteringId,
        opprettet = 2.mars(2020),
        status = null,
        linjer = ForrigeUtbetbetalingslinjeKoblendeListe(
            listOf(
                utbetalingslinje(periode = år(2020), beløp = 5000, uføregrad = 15),
            ),
        ).toNonEmptyList(),
    ),
)
