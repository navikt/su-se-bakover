package no.nav.su.se.bakover.client.oppdrag.utbetaling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class UtbetalingRequestTest {
    companion object {
        const val FAGOMRÅDE = "SUUFORE"
        const val BELØP = 1000
        const val SAKSBEHANDLER = "SU"
        val FNR = Fnr("12345678911")
        val yyyyMMdd = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val oppdragId = UUID30.randomUUID()
        val sakId = UUID.randomUUID()

        val oppdrag = no.nav.su.se.bakover.domain.oppdrag.Oppdrag(
            id = oppdragId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            utbetalinger = mutableListOf()
        )

        val nyOppdragslinjeId1 = UUID30.randomUUID()
        val nyOppdragslinjeId2 = UUID30.randomUUID()
        val nyUtbetaling = Utbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = nyOppdragslinjeId1,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = null,
                ),
                Utbetalingslinje(
                    id = nyOppdragslinjeId2,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = nyOppdragslinjeId1,
                )
            ),
            fnr = FNR
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
                    kodeKomponent = "SU",
                    nokkelAvstemming = "1577836800000000000",
                    tidspktMelding = "2020-01-01-01.00.00.000000"
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
                        refFagsystemId = null,
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456"))
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
                        refFagsystemId = oppdrag.id.toString(),
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456"))
                    )
                )
            )
        )
    }

    @Test
    fun `bygger utbetaling request til bruker uten eksisterende oppdragslinjer`() {
        val utbetalingRequest = toUtbetalingRequest(
            nyUtbetaling = NyUtbetaling(
                oppdrag = oppdrag,
                utbetaling = nyUtbetaling,
                attestant = Attestant("A123456"),
                avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay())
            ),
        )
        utbetalingRequest shouldBe utbetalingRequestFørstegangsbehandling
    }

    @Test
    fun `bygger simulering request til bruker som allerede har fått penger`() {
        val eksisterendeOppdragslinjeId = UUID30.randomUUID()
        val eksisterendeOppdrag = oppdrag.copy(
            utbetalinger = mutableListOf(
                Utbetaling(
                    oppdragsmelding = Oppdragsmelding(
                        status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                        originalMelding = "",
                        avstemmingsnøkkel = Avstemmingsnøkkel()
                    ),
                    kvittering = Kvittering(
                        utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                        originalKvittering = "someFakeData",
                        mottattTidspunkt = Tidspunkt.EPOCH.plusSeconds(10)
                    ),
                    utbetalingslinjer = listOf(
                        Utbetalingslinje(
                            id = eksisterendeOppdragslinjeId,
                            fraOgMed = 1.januar(2019),
                            tilOgMed = 31.desember(2019),
                            beløp = BELØP,
                            forrigeUtbetalingslinjeId = null,
                        )
                    ),
                    fnr = FNR
                )
            )
        )
        val nyOppdragslinjeid1 = UUID30.randomUUID()
        val nyOppdragslinjeid2 = UUID30.randomUUID()

        val nyUtbetaling = Utbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = nyOppdragslinjeid1,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = eksisterendeOppdragslinjeId,
                ),
                Utbetalingslinje(
                    id = nyOppdragslinjeid2,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = nyOppdragslinjeid1,
                )
            ),
            fnr = FNR
        )
        val utbetalingRequest = toUtbetalingRequest(
            nyUtbetaling = NyUtbetaling(
                oppdrag = eksisterendeOppdrag,
                utbetaling = nyUtbetaling,
                attestant = Attestant("A123456"),
                avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay())
            ),
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
                    kodeKomponent = "SU",
                    nokkelAvstemming = "1577836800000000000",
                    tidspktMelding = "2020-01-01-01.00.00.000000"
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
                        refFagsystemId = eksisterendeOppdrag.id.toString(),
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456"))
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
                        refFagsystemId = eksisterendeOppdrag.id.toString(),
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456"))
                    )
                )
            )
        )
    }
}
