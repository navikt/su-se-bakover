package no.nav.su.se.bakover.client.oppdrag.avstemming

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

internal class AvstemmingDataBuilderTest {
    @Test
    fun `Sjekk at vi bygger AvstemmingDataReqest riktig`() {
        val avstemmingId = UUID30.randomUUID()
        val expected = AvstemmingDataRequest(
            aksjon = Aksjonsdata(
                aksjonType = Aksjonsdata.AksjonType.DATA,
                kildeType = Aksjonsdata.KildeType.AVLEVERT,
                avstemmingType = Aksjonsdata.AvstemmingType.GRENSESNITTAVSTEMMING,
                mottakendeKomponentKode = "OS",
                brukerId = "SU",
                nokkelFom = "1583017200000000000",
                nokkelTom = "1583103600000000000",
                avleverendeAvstemmingId = avstemmingId.toString()
            ),
            total = AvstemmingDataRequest.Totaldata(
                totalAntall = 5,
                totalBelop = BigDecimal(18000),
                fortegn = AvstemmingDataRequest.Fortegn.TILLEGG
            ),
            periode = AvstemmingDataRequest.Periodedata(
                datoAvstemtFom = "2020030100",
                datoAvstemtTom = "2020030200"
            ),
            grunnlag = AvstemmingDataRequest.Grunnlagdata(
                godkjentAntall = 2,
                godkjentBelop = BigDecimal(1600),
                godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                varselAntall = 1,
                varselBelop = BigDecimal(1400),
                varselFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                avvistAntall = 1,
                avvistBelop = BigDecimal(10000),
                avvistFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                manglerAntall = 1,
                manglerBelop = BigDecimal(5000),
                manglerFortegn = AvstemmingDataRequest.Fortegn.TILLEGG
            ),
            detalj = listOf(
                AvstemmingDataRequest.Detaljdata(
                    detaljType = AvstemmingDataRequest.Detaljdata.Detaljtype.GODKJENT_MED_VARSEL,
                    offnr = "12345678910",
                    avleverendeTransaksjonNokkel = okMedVarselId.toString(),
                    tidspunkt = "2020-03-02-00.00.00.000000"
                ),
                AvstemmingDataRequest.Detaljdata(
                    detaljType = AvstemmingDataRequest.Detaljdata.Detaljtype.AVVIST,
                    offnr = "12345678910",
                    avleverendeTransaksjonNokkel = feildId.toString(),
                    tidspunkt = "2020-03-01-00.00.00.000000"
                ),
                AvstemmingDataRequest.Detaljdata(
                    detaljType = AvstemmingDataRequest.Detaljdata.Detaljtype.MANGLENDE_KVITTERING,
                    offnr = "12345678910",
                    avleverendeTransaksjonNokkel = manglerKvitteringId.toString(),
                    tidspunkt = "2020-03-02-00.00.00.000000"
                )
            )
        )

        AvstemmingDataBuilder(
            Avstemming(
                id = avstemmingId,
                opprettet = now(),
                fraOgMed = 1.mars(2020).atStartOfDay(zoneId).toTidspunkt(),
                tilOgMed = 2.mars(2020).atStartOfDay(zoneId).toTidspunkt(),
                utbetalinger = alleUtbetalinger(),
                avstemmingXmlRequest = null
            ),
        ).build() shouldBe expected
    }

    @Test
    fun `kast exception inkonsistens i data`() {
        assertThrows<IllegalStateException> {
            AvstemmingDataBuilder(
                Avstemming(
                    fraOgMed = 1.mars(2020).atStartOfDay(zoneId).toTidspunkt(),
                    tilOgMed = 2.mars(2020).atStartOfDay(zoneId).toTidspunkt(),
                    utbetalinger = alleUtbetalinger() + listOf(
                        Utbetaling.UtbetalingForSimulering(
                            utbetalingslinjer = emptyList(),
                            fnr = fnr,
                            type = Utbetaling.UtbetalingsType.NY,
                            oppdragId = UUID30.randomUUID(),
                            behandler = NavIdentBruker.Saksbehandler("Z123"),
                            avstemmingsnøkkel = Avstemmingsnøkkel()
                        )
                    ),
                    avstemmingXmlRequest = null
                ),
            ).build()
        }
    }
}

private val zoneId = ZoneId.of("Europe/Oslo")
fun lagUtbetalingLinje(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = Utbetalingslinje(
    id = UUID30.randomUUID(),
    opprettet = fraOgMed.atStartOfDay(zoneId).toTidspunkt(),
    fraOgMed = fraOgMed,
    tilOgMed = tilOgMed,
    forrigeUtbetalingslinjeId = null,
    beløp = beløp
)

fun lagUtbetaling(
    id: UUID30 = UUID30.randomUUID(),
    opprettet: LocalDate,
    status: Kvittering.Utbetalingsstatus?,
    linjer: List<Utbetalingslinje>,
    oppdragsmelding: Oppdragsmelding = Oppdragsmelding(
        originalMelding = "Melding",
        avstemmingsnøkkel = Avstemmingsnøkkel()
    )
) = when (status) {
    null -> Utbetaling.OversendtUtbetaling(
        id = id,
        opprettet = opprettet.atStartOfDay(zoneId).toTidspunkt(),
        simulering = simulering,
        oppdragsmelding = oppdragsmelding,
        utbetalingslinjer = linjer,
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        oppdragId = UUID30.randomUUID(),
        behandler = NavIdentBruker.Saksbehandler("Z123")
    )
    else -> Utbetaling.KvittertUtbetaling(
        id = id,
        opprettet = opprettet.atStartOfDay(zoneId).toTidspunkt(),
        simulering = simulering,
        kvittering = Kvittering(
            utbetalingsstatus = status,
            originalKvittering = "hallo",
            mottattTidspunkt = now()
        ),
        oppdragsmelding = oppdragsmelding,
        utbetalingslinjer = linjer,
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        oppdragId = UUID30.randomUUID(),
        behandler = NavIdentBruker.Saksbehandler("Z123")
    )
}

val fnr = Fnr("12345678910")
val ok1Id = UUID30.randomUUID()
val ok2Id = UUID30.randomUUID()
val okMedVarselId = UUID30.randomUUID()
val feildId = UUID30.randomUUID()
val manglerKvitteringId = UUID30.randomUUID()
private val simulering = Simulering(
    gjelderId = fnr,
    gjelderNavn = "",
    datoBeregnet = idag(),
    nettoBeløp = 0,
    periodeList = listOf()
)
fun alleUtbetalinger() = listOf(
    lagUtbetaling(
        id = ok1Id,
        opprettet = 1.mars(2020),
        status = Kvittering.Utbetalingsstatus.OK,
        linjer = listOf(
            lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 100),
            lagUtbetalingLinje(1.april(2020), 30.april(2020), 200)
        )
    ),
    lagUtbetaling(
        id = ok2Id,
        opprettet = 1.mars(2020),
        status = Kvittering.Utbetalingsstatus.OK,
        linjer = listOf(
            lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 600),
            lagUtbetalingLinje(1.april(2020), 30.april(2020), 700)
        )
    ),
    lagUtbetaling(
        id = okMedVarselId,
        opprettet = 2.mars(2020),
        status = Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
        linjer = listOf(
            lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 400),
            lagUtbetalingLinje(1.april(2020), 30.april(2020), 500),
            lagUtbetalingLinje(1.mai(2020), 31.mai(2020), 500)
        )
    ),
    lagUtbetaling(
        id = feildId,
        opprettet = 1.mars(2020),
        status = Kvittering.Utbetalingsstatus.FEIL,
        linjer = listOf(
            lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 1000),
            lagUtbetalingLinje(1.april(2020), 30.april(2020), 2000),
            lagUtbetalingLinje(1.mai(2020), 31.mai(2020), 3000),
            lagUtbetalingLinje(1.juni(2020), 30.juni(2020), 4000)
        )
    ),
    lagUtbetaling(
        id = manglerKvitteringId,
        opprettet = 2.mars(2020),
        status = null,
        linjer = listOf(
            lagUtbetalingLinje(1.januar(2020), 31.desember(2020), 5000)
        )
    )
)
