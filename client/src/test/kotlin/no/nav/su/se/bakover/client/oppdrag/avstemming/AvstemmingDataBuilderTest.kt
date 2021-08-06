package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

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
                totalBelop = 18000.toBigDecimal(),
                fortegn = AvstemmingDataRequest.Fortegn.TILLEGG
            ),
            periode = AvstemmingDataRequest.Periodedata(
                datoAvstemtFom = "2020030100",
                datoAvstemtTom = "2020030200"
            ),
            grunnlag = AvstemmingDataRequest.Grunnlagdata(
                godkjentAntall = 2,
                godkjentBelop = 1600.toBigDecimal(),
                godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                varselAntall = 1,
                varselBelop = 1400.toBigDecimal(),
                varselFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                avvistAntall = 1,
                avvistBelop = 10000.toBigDecimal(),
                avvistFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                manglerAntall = 1,
                manglerBelop = 5000.toBigDecimal(),
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
                opprettet = Tidspunkt.now(),
                fraOgMed = 1.mars(2020).atStartOfDay(zoneIdOslo).toTidspunkt(),
                tilOgMed = 2.mars(2020).atStartOfDay(zoneIdOslo).toTidspunkt(),
                utbetalinger = alleUtbetalinger(),
                avstemmingXmlRequest = null
            ),
        ).build() shouldBe expected
    }
}

internal val saksnummer = Saksnummer(2021)
internal val sakId = UUID.randomUUID()

internal fun lagUtbetalingLinje(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = Utbetalingslinje.Ny(
    id = UUID30.randomUUID(),
    opprettet = fraOgMed.atStartOfDay(zoneIdOslo).toTidspunkt(),
    fraOgMed = fraOgMed,
    tilOgMed = tilOgMed,
    forrigeUtbetalingslinjeId = null,
    beløp = beløp
)

internal fun lagUtbetaling(
    id: UUID30 = UUID30.randomUUID(),
    opprettet: LocalDate,
    status: Kvittering.Utbetalingsstatus?,
    linjer: NonEmptyList<Utbetalingslinje>,
    oppdragsmelding: Utbetalingsrequest = Utbetalingsrequest(
        value = "Melding"
    )
): Utbetaling.OversendtUtbetaling = when (status) {
    null -> Utbetaling.OversendtUtbetaling.UtenKvittering(
        id = id,
        opprettet = opprettet.atStartOfDay(zoneIdOslo).toTidspunkt(),
        sakId = sakId,
        saksnummer = saksnummer,
        simulering = simulering,
        utbetalingsrequest = oppdragsmelding,
        utbetalingslinjer = linjer,
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = NavIdentBruker.Saksbehandler("Z123")
    )
    else -> Utbetaling.OversendtUtbetaling.MedKvittering(
        id = id,
        opprettet = opprettet.atStartOfDay(zoneIdOslo).toTidspunkt(),
        saksnummer = saksnummer,
        sakId = sakId,
        simulering = simulering,
        kvittering = Kvittering(
            utbetalingsstatus = status,
            originalKvittering = "hallo",
            mottattTidspunkt = Tidspunkt.now()
        ),
        utbetalingsrequest = oppdragsmelding,
        utbetalingslinjer = linjer,
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = NavIdentBruker.Saksbehandler("Z123")
    )
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
    datoBeregnet = idag(),
    nettoBeløp = 0,
    periodeList = listOf()
)

internal fun alleUtbetalinger() = listOf(
    lagUtbetaling(
        id = ok1Id,
        opprettet = 1.mars(2020),
        status = Kvittering.Utbetalingsstatus.OK,
        linjer = nonEmptyListOf(
            lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 100),
            lagUtbetalingLinje(1.april(2020), 30.april(2020), 200)
        )
    ),
    lagUtbetaling(
        id = ok2Id,
        opprettet = 1.mars(2020),
        status = Kvittering.Utbetalingsstatus.OK,
        linjer = nonEmptyListOf(
            lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 600),
            lagUtbetalingLinje(1.april(2020), 30.april(2020), 700)
        )
    ),
    lagUtbetaling(
        id = okMedVarselId,
        opprettet = 2.mars(2020),
        status = Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
        linjer = nonEmptyListOf(
            lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 400),
            lagUtbetalingLinje(1.april(2020), 30.april(2020), 500),
            lagUtbetalingLinje(1.mai(2020), 31.mai(2020), 500)
        )
    ),
    lagUtbetaling(
        id = feildId,
        opprettet = 1.mars(2020),
        status = Kvittering.Utbetalingsstatus.FEIL,
        linjer = nonEmptyListOf(
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
        linjer = nonEmptyListOf(
            lagUtbetalingLinje(1.januar(2020), 31.desember(2020), 5000)
        )
    )
)
