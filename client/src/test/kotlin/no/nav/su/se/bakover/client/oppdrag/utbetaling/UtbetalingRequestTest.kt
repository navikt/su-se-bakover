package no.nav.su.se.bakover.client.oppdrag.utbetaling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class UtbetalingRequestTest {
    companion object {
        const val FAGOMRÅDE = "SUUFORE"
        const val BELØP = 1000
        const val SAKSBEHANDLER = "SU"
        val FNR = Fnr("12345678911")
        val yyyyMMdd = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

        val oppdragId = UUID30.randomUUID()
        val sakId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()

        val oppdrag = no.nav.su.se.bakover.domain.oppdrag.Oppdrag(
            id = oppdragId,
            opprettet = Instant.EPOCH,
            sakId = sakId,
            utbetalinger = mutableListOf()
        )

        val nyOppdragslinjeId1 = UUID30.randomUUID()
        val nyOppdragslinjeId2 = UUID30.randomUUID()
        val nyUtbetaling = Utbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = nyOppdragslinjeId1,
                    fom = 1.januar(2020),
                    tom = 30.april(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = null,
                ),
                Utbetalingslinje(
                    id = nyOppdragslinjeId2,
                    fom = 1.mai(2020),
                    tom = 31.desember(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = nyOppdragslinjeId1,
                )
            ),
            behandlingId = behandlingId
        )

        val utbetalingRequestFørstegangsbehandling = UtbetalingRequest(
            oppdragRequest = UtbetalingRequest.OppdragRequest(
                oppdragGjelderId = FNR.fnr,
                saksbehId = SAKSBEHANDLER,
                fagsystemId = oppdragId.toString(),
                kodeEndring = UtbetalingRequest.KodeEndring.NY,
                kodeFagomraade = FAGOMRÅDE,
                utbetFrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND,
                datoOppdragGjelderFom = LocalDate.EPOCH.format(yyyyMMdd),
                oppdragsEnheter = listOf(
                    UtbetalingRequest.OppdragsEnhet(
                        datoEnhetFom = LocalDate.EPOCH.format(yyyyMMdd),
                        enhet = "8020",
                        typeEnhet = "BOS"
                    )
                ),
                avstemming = UtbetalingRequest.Avstemming(
                    kodeKomponent = "SUUFORE",
                    nokkelAvstemming = nyUtbetaling.id.toString(),
                    tidspktMelding = "1970-01-01-01.00.00.000000"
                ),
                kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING,
                oppdragslinjer = listOf(
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
                        utbetalesTilId = FNR.fnr,
                        refDelytelseId = null,
                        refFagsystemId = null
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
                        utbetalesTilId = FNR.fnr,
                        refDelytelseId = nyUtbetaling.utbetalingslinjer[0].id.toString(),
                        refFagsystemId = oppdrag.id.toString()
                    )
                )
            )
        )
    }

    @Test
    fun `bygger utbetaling request til bruker uten eksisterende oppdragslinjer`() {

        val utbetalingRequest = toUtbetalingRequest(
            oppdrag = oppdrag,
            utbetaling = nyUtbetaling,
            oppdragGjelder = FNR,
            clock = clock
        )
        utbetalingRequest shouldBe utbetalingRequestFørstegangsbehandling
    }

    @Test
    fun `bygger simulering request til bruker som allerede har fått penger`() {
        val eksisterendeOppdragslinjeId = UUID30.randomUUID()
        val eksisterendeOppdrag = oppdrag.copy(
            utbetalinger = mutableListOf(
                Utbetaling(
                    utbetalingslinjer = listOf(
                        Utbetalingslinje(
                            id = eksisterendeOppdragslinjeId,
                            fom = 1.januar(2019),
                            tom = 31.desember(2019),
                            beløp = BELØP,
                            forrigeUtbetalingslinjeId = null,
                        )
                    ),
                    behandlingId = behandlingId,
                    kvittering = Kvittering(
                        utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                        originalKvittering = "someFakeData",
                        mottattTidspunkt = Instant.EPOCH.plusSeconds(10)
                    )
                )
            )
        )
        val nyOppdragslinjeid1 = UUID30.randomUUID()
        val nyOppdragslinjeid2 = UUID30.randomUUID()

        val nyUtbetaling = Utbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = nyOppdragslinjeid1,
                    fom = 1.januar(2020),
                    tom = 30.april(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = eksisterendeOppdragslinjeId,
                ),
                Utbetalingslinje(
                    id = nyOppdragslinjeid2,
                    fom = 1.mai(2020),
                    tom = 31.desember(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = nyOppdragslinjeid1,
                )
            ),
            behandlingId = behandlingId
        )
        val utbetalingRequest = toUtbetalingRequest(
            oppdrag = eksisterendeOppdrag,
            utbetaling = nyUtbetaling,
            oppdragGjelder = FNR,
            clock = clock
        )

        utbetalingRequest shouldBe UtbetalingRequest(
            oppdragRequest = UtbetalingRequest.OppdragRequest(
                oppdragGjelderId = FNR.fnr,
                saksbehId = SAKSBEHANDLER,
                fagsystemId = oppdragId.toString(),
                kodeEndring = UtbetalingRequest.KodeEndring.ENDRING,
                kodeFagomraade = FAGOMRÅDE,
                utbetFrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND,
                datoOppdragGjelderFom = LocalDate.EPOCH.format(yyyyMMdd),
                oppdragsEnheter = listOf(
                    UtbetalingRequest.OppdragsEnhet(
                        datoEnhetFom = LocalDate.EPOCH.format(yyyyMMdd),
                        enhet = "8020",
                        typeEnhet = "BOS"
                    )
                ),
                avstemming = UtbetalingRequest.Avstemming(
                    kodeKomponent = "SUUFORE",
                    nokkelAvstemming = nyUtbetaling.id.toString(),
                    tidspktMelding = "1970-01-01-01.00.00.000000"
                ),
                kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING,
                oppdragslinjer = listOf(
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
                        utbetalesTilId = FNR.fnr,
                        refDelytelseId = eksisterendeOppdragslinjeId.toString(),
                        refFagsystemId = eksisterendeOppdrag.id.toString()
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
                        utbetalesTilId = FNR.fnr,
                        refDelytelseId = nyOppdragslinjeid1.toString(),
                        refFagsystemId = eksisterendeOppdrag.id.toString()
                    )
                )
            )
        )
    }
}
