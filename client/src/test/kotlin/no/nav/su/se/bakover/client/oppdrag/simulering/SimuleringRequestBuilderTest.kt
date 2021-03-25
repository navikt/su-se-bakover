package no.nav.su.se.bakover.client.oppdrag.simulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequestTest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class SimuleringRequestBuilderTest {

    @Test
    fun `bygger simulering request til bruker uten eksisterende oppdragslinjer`() {
        val utbetalingsRequest = UtbetalingRequestTest.utbetalingRequestFørstegangsbehandling.oppdragRequest
        SimuleringRequestBuilder(utbetalingsRequest).build().request.also {
            it.oppdrag.let { oppdrag ->
                oppdrag.oppdragGjelderId shouldBe utbetalingsRequest.oppdragGjelderId
                oppdrag.saksbehId shouldBe utbetalingsRequest.saksbehId
                oppdrag.fagsystemId shouldBe utbetalingsRequest.fagsystemId
                oppdrag.kodeEndring shouldBe utbetalingsRequest.kodeEndring.value
                oppdrag.kodeFagomraade shouldBe utbetalingsRequest.kodeFagomraade
                oppdrag.utbetFrekvens shouldBe utbetalingsRequest.utbetFrekvens.value
                oppdrag.datoOppdragGjelderFom shouldBe utbetalingsRequest.datoOppdragGjelderFom
                oppdrag.enhet[0].datoEnhetFom shouldBe utbetalingsRequest.oppdragsEnheter[0].datoEnhetFom
                oppdrag.enhet[0].enhet shouldBe utbetalingsRequest.oppdragsEnheter[0].enhet
                oppdrag.enhet[0].typeEnhet shouldBe utbetalingsRequest.oppdragsEnheter[0].typeEnhet
                oppdrag.oppdragslinje[0].also { oppdragslinje ->
                    oppdragslinje.delytelseId shouldBe utbetalingsRequest.oppdragslinjer[0].delytelseId
                    oppdragslinje.kodeEndringLinje shouldBe utbetalingsRequest.oppdragslinjer[0].kodeEndringLinje.value
                    oppdragslinje.sats shouldBe BigDecimal(utbetalingsRequest.oppdragslinjer[0].sats)
                    oppdragslinje.typeSats shouldBe utbetalingsRequest.oppdragslinjer[0].typeSats.value
                    oppdragslinje.datoVedtakFom shouldBe utbetalingsRequest.oppdragslinjer[0].datoVedtakFom
                    oppdragslinje.datoVedtakTom shouldBe utbetalingsRequest.oppdragslinjer[0].datoVedtakTom
                    oppdragslinje.utbetalesTilId shouldBe utbetalingsRequest.oppdragslinjer[0].utbetalesTilId
                    oppdragslinje.refDelytelseId shouldBe utbetalingsRequest.oppdragslinjer[0].refDelytelseId
                    oppdragslinje.refFagsystemId shouldBe utbetalingsRequest.oppdragslinjer[0].refFagsystemId
                    oppdragslinje.kodeKlassifik shouldBe utbetalingsRequest.oppdragslinjer[0].kodeKlassifik
                    oppdragslinje.fradragTillegg shouldBe utbetalingsRequest.oppdragslinjer[0].fradragTillegg.value.let { FradragTillegg.valueOf(it) }
                    oppdragslinje.saksbehId shouldBe utbetalingsRequest.oppdragslinjer[0].saksbehId
                    oppdragslinje.brukKjoreplan shouldBe utbetalingsRequest.oppdragslinjer[0].brukKjoreplan
                    oppdragslinje.attestant[0].attestantId shouldBe utbetalingsRequest.oppdragslinjer[0].saksbehId
                    oppdragslinje.kodeStatusLinje shouldBe null
                    oppdragslinje.datoStatusFom shouldBe null
                }
            }
            it.simuleringsPeriode.let { simuleringsPeriode ->
                simuleringsPeriode.datoSimulerFom shouldBe "2020-01-01"
                simuleringsPeriode.datoSimulerTom shouldBe "2020-12-31"
            }
        }
    }

    @Test
    fun `bygger simulering request ved endring av eksisterende oppdragslinjer`() {
        val endretUtbetalingslinje = Utbetalingslinje.Endring(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            beløp = UtbetalingRequestTest.BELØP,
            forrigeUtbetalingslinjeId = UUID30.randomUUID(),
            statusendring = Utbetalingslinje.Statusendring(
                status = Utbetalingslinje.LinjeStatus.OPPHØRT,
                fraOgMed = 1.februar(2020),
            ),
        )
        val endring = UtbetalingRequestTest.nyUtbetaling.copy(
            type = Utbetaling.UtbetalingsType.OPPHØR,
            avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
            utbetalingslinjer = listOf(endretUtbetalingslinje),
        )

        val utbetalingsRequest = toUtbetalingRequest(endring).oppdragRequest
        SimuleringRequestBuilder(utbetalingsRequest).build().request.let {
            it.oppdrag.let {
                it.oppdragGjelderId shouldBe utbetalingsRequest.oppdragGjelderId
                it.saksbehId shouldBe utbetalingsRequest.saksbehId
                it.fagsystemId shouldBe utbetalingsRequest.fagsystemId
                it.kodeEndring shouldBe utbetalingsRequest.kodeEndring.value
                it.kodeFagomraade shouldBe utbetalingsRequest.kodeFagomraade
                it.utbetFrekvens shouldBe utbetalingsRequest.utbetFrekvens.value
                it.datoOppdragGjelderFom shouldBe utbetalingsRequest.datoOppdragGjelderFom
                it.enhet[0].datoEnhetFom shouldBe utbetalingsRequest.oppdragsEnheter[0].datoEnhetFom
                it.enhet[0].enhet shouldBe utbetalingsRequest.oppdragsEnheter[0].enhet
                it.enhet[0].typeEnhet shouldBe utbetalingsRequest.oppdragsEnheter[0].typeEnhet
                it.oppdragslinje[0].also { oppdragslinje ->
                    oppdragslinje.delytelseId shouldBe utbetalingsRequest.oppdragslinjer[0].delytelseId
                    oppdragslinje.kodeEndringLinje shouldBe utbetalingsRequest.oppdragslinjer[0].kodeEndringLinje.value
                    oppdragslinje.sats shouldBe BigDecimal(utbetalingsRequest.oppdragslinjer[0].sats)
                    oppdragslinje.typeSats shouldBe utbetalingsRequest.oppdragslinjer[0].typeSats.value
                    oppdragslinje.datoVedtakFom shouldBe utbetalingsRequest.oppdragslinjer[0].datoVedtakFom
                    oppdragslinje.datoVedtakTom shouldBe utbetalingsRequest.oppdragslinjer[0].datoVedtakTom
                    oppdragslinje.utbetalesTilId shouldBe utbetalingsRequest.oppdragslinjer[0].utbetalesTilId
                    oppdragslinje.refDelytelseId shouldBe utbetalingsRequest.oppdragslinjer[0].refDelytelseId
                    oppdragslinje.refFagsystemId shouldBe utbetalingsRequest.oppdragslinjer[0].refFagsystemId
                    oppdragslinje.kodeKlassifik shouldBe utbetalingsRequest.oppdragslinjer[0].kodeKlassifik
                    oppdragslinje.fradragTillegg.value() shouldBe utbetalingsRequest.oppdragslinjer[0].fradragTillegg.value
                    oppdragslinje.saksbehId shouldBe utbetalingsRequest.oppdragslinjer[0].saksbehId
                    oppdragslinje.brukKjoreplan shouldBe utbetalingsRequest.oppdragslinjer[0].brukKjoreplan
                    oppdragslinje.attestant[0].attestantId shouldBe utbetalingsRequest.oppdragslinjer[0].saksbehId
                    oppdragslinje.kodeStatusLinje.value() shouldBe "OPPH"
                    oppdragslinje.datoStatusFom shouldBe "2020-02-01"
                }
            }
            it.simuleringsPeriode.let { simuleringsPeriode ->
                simuleringsPeriode.datoSimulerFom shouldBe "2020-01-01"
                simuleringsPeriode.datoSimulerTom shouldBe "2020-04-30"
            }
        }
    }
}
