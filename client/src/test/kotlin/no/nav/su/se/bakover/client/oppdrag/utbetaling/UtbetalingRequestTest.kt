package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetalingslinje
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
        private val clock = TikkendeKlokke()
        val nyUtbetaling = Utbetaling.UtbetalingForSimulering(
            opprettet = clock.nextTidspunkt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = FNR,
            utbetalingslinjer = nonEmptyListOf(
                utbetalingslinje(
                    id = nyOppdragslinjeId1,
                    periode = januar(2020)..april(2020),
                    beløp = BELØP,
                    opprettet = clock.nextTidspunkt(),
                ),
                utbetalingslinje(
                    id = nyOppdragslinjeId2,
                    periode = mai(2020)..desember(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = nyOppdragslinjeId1,
                    uføregrad = 70,
                    opprettet = clock.nextTidspunkt(),
                ),
            ),
            behandler = NavIdentBruker.Attestant("A123456"),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        )

        val utbetalingRequestFørstegangsutbetaling = UtbetalingRequest(
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
                        brukKjoreplan = UtbetalingRequest.Oppdragslinje.Kjøreplan.NEI,
                        saksbehId = "SU",
                        utbetalesTilId = FNR.toString(),
                        refDelytelseId = null,
                        refFagsystemId = null,
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456")),
                        grad = UtbetalingRequest.Oppdragslinje.Grad(
                            typeGrad = UtbetalingRequest.Oppdragslinje.TypeGrad.UFOR,
                            grad = 50,
                        ),
                        utbetalingId = nyUtbetaling.id.toString(),
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
                        brukKjoreplan = UtbetalingRequest.Oppdragslinje.Kjøreplan.NEI,
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
                        utbetalingId = nyUtbetaling.id.toString(),
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
        utbetalingRequest shouldBe utbetalingRequestFørstegangsutbetaling
    }

    @Test
    fun `bygger simulering request til bruker som allerede har fått penger`() {
        val eksisterendeOppdragslinjeId = UUID30.randomUUID()
        val nyOppdragslinjeid1 = UUID30.randomUUID()
        val nyOppdragslinjeid2 = UUID30.randomUUID()
        val clock = TikkendeKlokke()
        val nyUtbetaling = Utbetaling.UtbetalingForSimulering(
            opprettet = fixedTidspunkt,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = FNR,
            utbetalingslinjer = nonEmptyListOf(
                utbetalingslinje(
                    id = nyOppdragslinjeid1,
                    periode = januar(2020)..april(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = eksisterendeOppdragslinjeId,
                    opprettet = clock.nextTidspunkt(),
                ),
                utbetalingslinje(
                    id = nyOppdragslinjeid2,
                    periode = mai(2020)..desember(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = nyOppdragslinjeid1,
                    opprettet = clock.nextTidspunkt(),
                ),
            ),
            behandler = NavIdentBruker.Attestant("A123456"),
            avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
            sakstype = Sakstype.UFØRE,
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
                        brukKjoreplan = UtbetalingRequest.Oppdragslinje.Kjøreplan.NEI,
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
                        utbetalingId = nyUtbetaling.id.toString(),
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
                        brukKjoreplan = UtbetalingRequest.Oppdragslinje.Kjøreplan.NEI,
                        saksbehId = "SU",
                        utbetalesTilId = FNR.toString(),
                        refDelytelseId = nyOppdragslinjeid1.toString(),
                        refFagsystemId = saksnummer.toString(),
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456")),
                        grad = UtbetalingRequest.Oppdragslinje.Grad(
                            typeGrad = UtbetalingRequest.Oppdragslinje.TypeGrad.UFOR,
                            grad = 50,
                        ),
                        utbetalingId = nyUtbetaling.id.toString(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `bygger en request for endring av eksisterende linjer`() {
        val endring = nyUtbetaling.copy(
            avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = nyUtbetaling.sisteUtbetalingslinje(),
                    virkningsperiode = Periode.create(1.januar(2020), nyUtbetaling.sisteUtbetalingslinje().periode.tilOgMed),
                    clock = Clock.systemUTC(),
                ),
            ),
        )

        val stans = nyUtbetaling.copy(
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
                    brukKjoreplan = UtbetalingRequest.Oppdragslinje.Kjøreplan.NEI,
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
                    utbetalingId = nyUtbetaling.id.toString(),
                ),
            ),
        ),
    )
}
