package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class UtbetalingRequestTest {
    companion object {
        const val FAGOMRÅDE = "SUUFORE"
        const val BELØP = 1000
        const val SAKSBEHANDLER = "SU"
        val FNR = Fnr("12345678911")
        val yyyyMMdd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        private val nyOppdragslinjeId1 = UUID30.randomUUID()
        private val nyOppdragslinjeId2 = UUID30.randomUUID()
        val nyUtbetaling = Utbetaling.UtbetalingForSimulering(
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = nyOppdragslinjeId1,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(50),
                ),
                Utbetalingslinje.Ny(
                    id = nyOppdragslinjeId2,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = nyOppdragslinjeId1,
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
            fnr = FNR,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Attestant("A123456"),
            avstemmingsnøkkel = Avstemmingsnøkkel(),
        )

        val utbetalingRequestFørstegangsbehandling = UtbetalingRequest(
            oppdragRequest = UtbetalingRequest.OppdragRequest(
                oppdragGjelderId = FNR.toString(),
                saksbehId = SAKSBEHANDLER,
                fagsystemId = saksnummer.toString(),
                kodeEndring = UtbetalingRequest.KodeEndring.NY,
                kodeFagomraade = FAGOMRÅDE,
                utbetFrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND,
                datoOppdragGjelderFom = LocalDate.EPOCH.format(yyyyMMdd),
                oppdragsEnheter = listOf(
                    UtbetalingRequest.OppdragsEnhet(
                        datoEnhetFom = LocalDate.EPOCH.format(yyyyMMdd),
                        enhet = "8020",
                        typeEnhet = "BOS",
                    ),
                ),
                avstemming = UtbetalingRequest.Avstemming(
                    kodeKomponent = "SU",
                    nokkelAvstemming = "1577833200000000000",
                    tidspktMelding = "2020-01-01-00.00.00.000000",
                ),
                kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING,
                oppdragslinjer = nonEmptyListOf(
                    UtbetalingRequest.Oppdragslinje(
                        kodeStatusLinje = null,
                        datoStatusFom = null,
                        kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY,
                        delytelseId = nyUtbetaling.utbetalingslinjer[0].id.toString(),
                        kodeKlassifik = "SUUFORE",
                        datoVedtakFom = "2020-01-01",
                        datoVedtakTom = "2020-04-30",
                        sats = BELØP.toString(),
                        fradragTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG,
                        typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND,
                        brukKjoreplan = "N",
                        saksbehId = "SU",
                        utbetalesTilId = FNR.toString(),
                        refDelytelseId = null,
                        refFagsystemId = null,
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456")),
                        grad = UtbetalingRequest.Oppdragslinje.Grad(
                            typeGrad = UtbetalingRequest.Oppdragslinje.TypeGrad.UFOR,
                            grad = 50,
                        ),
                    ),
                    UtbetalingRequest.Oppdragslinje(
                        kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY,
                        delytelseId = nyUtbetaling.utbetalingslinjer[1].id.toString(),
                        kodeKlassifik = "SUUFORE",
                        datoVedtakFom = "2020-05-01",
                        datoVedtakTom = "2020-12-31",
                        sats = BELØP.toString(),
                        fradragTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG,
                        typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND,
                        brukKjoreplan = "N",
                        saksbehId = "SU",
                        utbetalesTilId = FNR.toString(),
                        refDelytelseId = nyUtbetaling.utbetalingslinjer[0].id.toString(),
                        refFagsystemId = saksnummer.toString(),
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456")),
                        kodeStatusLinje = null,
                        datoStatusFom = null,
                        grad = UtbetalingRequest.Oppdragslinje.Grad(
                            typeGrad = UtbetalingRequest.Oppdragslinje.TypeGrad.UFOR,
                            grad = 70,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `bygger utbetaling request til bruker uten eksisterende oppdragslinjer`() {
        val utbetalingRequest = toUtbetalingRequest(
            utbetaling = nyUtbetaling.copy(avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay())),
        )
        utbetalingRequest shouldBe utbetalingRequestFørstegangsbehandling
    }

    @Test
    fun `bygger simulering request til bruker som allerede har fått penger`() {
        val eksisterendeOppdragslinjeId = UUID30.randomUUID()
        val nyOppdragslinjeid1 = UUID30.randomUUID()
        val nyOppdragslinjeid2 = UUID30.randomUUID()

        val nyUtbetaling = Utbetaling.UtbetalingForSimulering(
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = nyOppdragslinjeid1,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = eksisterendeOppdragslinjeId,
                    uføregrad = Uføregrad.parse(50),
                ),
                Utbetalingslinje.Ny(
                    id = nyOppdragslinjeid2,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = nyOppdragslinjeid1,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            fnr = FNR,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Attestant("A123456"),
            avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
        )
        val utbetalingRequest = toUtbetalingRequest(utbetaling = nyUtbetaling)

        utbetalingRequest shouldBe UtbetalingRequest(
            oppdragRequest = UtbetalingRequest.OppdragRequest(
                oppdragGjelderId = FNR.toString(),
                saksbehId = SAKSBEHANDLER,
                fagsystemId = saksnummer.toString(),
                kodeEndring = UtbetalingRequest.KodeEndring.ENDRING,
                kodeFagomraade = FAGOMRÅDE,
                utbetFrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND,
                datoOppdragGjelderFom = LocalDate.EPOCH.format(yyyyMMdd),
                oppdragsEnheter = listOf(
                    UtbetalingRequest.OppdragsEnhet(
                        datoEnhetFom = LocalDate.EPOCH.format(yyyyMMdd),
                        enhet = "8020",
                        typeEnhet = "BOS",
                    ),
                ),
                avstemming = UtbetalingRequest.Avstemming(
                    kodeKomponent = "SU",
                    nokkelAvstemming = "1577833200000000000",
                    tidspktMelding = "2020-01-01-00.00.00.000000",
                ),
                kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING,
                oppdragslinjer = nonEmptyListOf(
                    UtbetalingRequest.Oppdragslinje(
                        kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY,
                        delytelseId = nyUtbetaling.utbetalingslinjer[0].id.toString(),
                        kodeKlassifik = "SUUFORE",
                        datoVedtakFom = "2020-01-01",
                        datoVedtakTom = "2020-04-30",
                        sats = BELØP.toString(),
                        fradragTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG,
                        typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND,
                        brukKjoreplan = "N",
                        saksbehId = "SU",
                        utbetalesTilId = FNR.toString(),
                        refDelytelseId = eksisterendeOppdragslinjeId.toString(),
                        refFagsystemId = saksnummer.toString(),
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456")),
                        kodeStatusLinje = null,
                        datoStatusFom = null,
                        grad = UtbetalingRequest.Oppdragslinje.Grad(
                            typeGrad = UtbetalingRequest.Oppdragslinje.TypeGrad.UFOR,
                            grad = 50,
                        ),
                    ),
                    UtbetalingRequest.Oppdragslinje(
                        kodeStatusLinje = null,
                        datoStatusFom = null,
                        kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY,
                        delytelseId = nyUtbetaling.utbetalingslinjer[1].id.toString(),
                        kodeKlassifik = "SUUFORE",
                        datoVedtakFom = "2020-05-01",
                        datoVedtakTom = "2020-12-31",
                        sats = BELØP.toString(),
                        fradragTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG,
                        typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND,
                        brukKjoreplan = "N",
                        saksbehId = "SU",
                        utbetalesTilId = FNR.toString(),
                        refDelytelseId = nyOppdragslinjeid1.toString(),
                        refFagsystemId = saksnummer.toString(),
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456")),
                        grad = UtbetalingRequest.Oppdragslinje.Grad(
                            typeGrad = UtbetalingRequest.Oppdragslinje.TypeGrad.UFOR,
                            grad = 50,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `bygger en request for endring av eksisterende linjer`() {
        val endring = nyUtbetaling.copy(
            type = Utbetaling.UtbetalingsType.OPPHØR,
            avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = nyUtbetaling.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.januar(2020),
                    clock = Clock.systemUTC(),
                ),
            ),
        )

        val stans = nyUtbetaling.copy(
            type = Utbetaling.UtbetalingsType.STANS,
            avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinje = nyUtbetaling.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.januar(2020),
                    clock = Clock.systemUTC(),
                ),
            ),
        )

        val gjenoppta = nyUtbetaling.copy(
            type = Utbetaling.UtbetalingsType.GJENOPPTA,
            avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Reaktivering(
                    utbetalingslinje = nyUtbetaling.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.januar(2020),
                    clock = Clock.systemUTC(),
                ),
            ),
        )

        val utbetalingRequest = toUtbetalingRequest(endring)
        val stansUtbetalingRequest = toUtbetalingRequest(stans)
        val gjenopptaUtbetalingRequest = toUtbetalingRequest(gjenoppta)

        utbetalingRequest shouldBe expected(UtbetalingRequest.Oppdragslinje.KodeStatusLinje.OPPHØR)
        stansUtbetalingRequest shouldBe expected(UtbetalingRequest.Oppdragslinje.KodeStatusLinje.HVIL)
        gjenopptaUtbetalingRequest shouldBe expected(UtbetalingRequest.Oppdragslinje.KodeStatusLinje.REAKTIVER)
    }

    private fun expected(status: UtbetalingRequest.Oppdragslinje.KodeStatusLinje) = UtbetalingRequest(
        oppdragRequest = UtbetalingRequest.OppdragRequest(

            oppdragGjelderId = FNR.toString(),
            saksbehId = SAKSBEHANDLER,
            fagsystemId = saksnummer.toString(),
            kodeEndring = UtbetalingRequest.KodeEndring.ENDRING,
            kodeFagomraade = FAGOMRÅDE,
            utbetFrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND,
            datoOppdragGjelderFom = LocalDate.EPOCH.format(yyyyMMdd),
            oppdragsEnheter = listOf(
                UtbetalingRequest.OppdragsEnhet(
                    datoEnhetFom = LocalDate.EPOCH.format(yyyyMMdd),
                    enhet = "8020",
                    typeEnhet = "BOS",
                ),
            ),
            avstemming = UtbetalingRequest.Avstemming(
                kodeKomponent = "SU",
                nokkelAvstemming = "1577833200000000000",
                tidspktMelding = "2020-01-01-00.00.00.000000",
            ),
            kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING,
            oppdragslinjer = nonEmptyListOf(
                UtbetalingRequest.Oppdragslinje(
                    kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.ENDRING,
                    delytelseId = nyUtbetaling.utbetalingslinjer[1].id.toString(),
                    kodeKlassifik = "SUUFORE",
                    datoVedtakFom = "2020-05-01",
                    datoVedtakTom = "2020-12-31",
                    sats = BELØP.toString(),
                    fradragTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG,
                    typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND,
                    brukKjoreplan = "N",
                    saksbehId = "SU",
                    utbetalesTilId = FNR.toString(),
                    refDelytelseId = null,
                    refFagsystemId = null,
                    attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456")),
                    kodeStatusLinje = status,
                    datoStatusFom = "2020-01-01",
                    grad = UtbetalingRequest.Oppdragslinje.Grad(
                        typeGrad = UtbetalingRequest.Oppdragslinje.TypeGrad.UFOR,
                        grad = 70,
                    ),
                ),
            ),
        ),
    )
}
